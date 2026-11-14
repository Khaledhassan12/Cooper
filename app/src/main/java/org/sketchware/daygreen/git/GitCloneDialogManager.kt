package org.sketchware.daygreen.git

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.besome.sketch.design.DesignActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import extensions.anbui.daydream.file.FilesTools
import mod.hey.studios.project.ProjectTracker
import mod.hey.studios.project.backup.BackupFactory
import org.eclipse.jgit.lib.ProgressMonitor
import org.sketchware.daygreen.git.activities.GitSettingsActivity
import pro.sketchware.databinding.DialogGitCloneBinding
import pro.sketchware.databinding.DialogGitProgressBinding
import pro.sketchware.utility.SketchwareUtil
import a.a.a.lC
import java.io.File
import kotlin.concurrent.thread

object GitCloneDialogManager {

    private const val TAG = "GitCloneDialogManager"

    @JvmStatic
    fun showCloneDialog(activity: Activity, onCompleteRunnable: Runnable?) {
        val binding = DialogGitCloneBinding.inflate(activity.layoutInflater)

        val inputDialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        inputDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding.btnCancel.setOnClickListener { inputDialog.dismiss() }

        binding.btnOk.setOnClickListener {
            val repoUrl = binding.etRepoUrl.text?.toString()?.trim() ?: ""
            var branch = binding.etBranch.text?.toString()?.trim() ?: ""

            if (repoUrl.isEmpty()) {
                SketchwareUtil.toast("Please enter a repository URL")
                return@setOnClickListener
            }

            if (branch.isEmpty()) {
                branch = "main"
            }

            inputDialog.dismiss()
            startCloning(activity, repoUrl, branch, onCompleteRunnable)
        }

        inputDialog.show()
        setupDialogWindow(activity, inputDialog)
    }

    @JvmStatic
    private fun startCloning(
        activity: Activity,
        repoUrl: String,
        branch: String,
        onCompleteRunnable: Runnable?
    ) {
        val progressBinding = DialogGitProgressBinding.inflate(activity.layoutInflater)

        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setView(progressBinding.root)
            .setCancelable(false)
            .create()

        progressDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        var isCancelled = false

        progressBinding.btnCancelProgress.setOnClickListener {
            isCancelled = true
            progressBinding.tvProgressStatus.text = "Cancelling clone..."
        }

        progressDialog.show()
        setupDialogWindow(activity, progressDialog)

        thread {
            val scId = BackupFactory.getNewScId()
            val targetGitDir: File = GitManager.getGitDir(scId)

            val monitor = object : ProgressMonitor {
                private var currentTask = "Cloning repository..."
                private var totalWork = 0

                override fun start(totalTasks: Int) {}

                override fun beginTask(title: String, totalWork: Int) {
                    this.currentTask = title
                    this.totalWork = totalWork
                    updateUI(title, 0, totalWork)
                }

                override fun update(completed: Int) {
                    if (totalWork > 0) {
                        updateUI(currentTask, completed, totalWork)
                    } else {
                        updateUI(currentTask, completed, -1)
                    }
                }

                override fun endTask() {}

                override fun isCancelled(): Boolean = isCancelled

                private fun updateUI(title: String, completed: Int, total: Int) {
                    activity.runOnUiThread {
                        if (total > 0) {
                            progressBinding.tvProgressStatus.text = "$title ($completed/$total)"
                            progressBinding.progressIndicator.isIndeterminate = false
                            progressBinding.progressIndicator.max = total
                            progressBinding.progressIndicator.progress = completed
                        } else {
                            progressBinding.tvProgressStatus.text = title
                            progressBinding.progressIndicator.isIndeterminate = true
                        }
                    }
                }
            }

            try {
                GitManager.cloneRepo(activity, repoUrl, branch, targetGitDir, monitor)

                if (isCancelled) {
                    try {
                        FilesTools.deleteFileOrDirectory(targetGitDir.toPath())
                    } catch (ignored: Exception) {}
                    activity.runOnUiThread {
                        progressDialog.dismiss()
                        SketchwareUtil.toast("Clone cancelled")
                    }
                    return@thread
                }

                val restored = GitManager.restoreProjectFromGitDir(scId)

                // Display checking status
                activity.runOnUiThread {
                    progressBinding.tvProgressTitle.text = "Checking project..."
                    progressBinding.tvProgressStatus.text = "Ensuring project is registered..."
                    progressBinding.progressIndicator.isIndeterminate = true
                }

                // Give time for dialog to be visible briefly
                Thread.sleep(800)

                val exists = restored && lC.b(scId) != null

                activity.runOnUiThread {
                    progressDialog.dismiss()
                    if (exists) {
                        SketchwareUtil.toast("Project cloned successfully! ID: $scId")
                        onCompleteRunnable?.run()
                        openProject(activity, scId)
                    } else {
                        SketchwareUtil.toastError("Project is not registered after clone (ID: $scId)")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Cloning failed: ", e)
                try {
                    FilesTools.deleteFileOrDirectory(targetGitDir.toPath())
                } catch (ignored: Exception) {}
                activity.runOnUiThread {
                    progressDialog.dismiss()
                    SketchwareUtil.toastError("Clone failed: ${e.message}")
                }
            }
        }
    }

    @JvmStatic
    private fun openProject(activity: Activity, scId: String) {
        ProjectTracker.setScId(scId)
        val intent = Intent(activity, DesignActivity::class.java).apply {
            putExtra("sc_id", scId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        activity.startActivity(intent)
    }

    @JvmStatic
    fun showInitGitDialog(activity: Activity, scId: String) {
        val username = GitConfiguration.getUsername(activity)
        val password = GitConfiguration.getPassword(activity)
        val name = GitConfiguration.getUserName(activity)
        val email = GitConfiguration.getUserEmail(activity)

        if (username.isEmpty() || password.isEmpty() || name.isEmpty() || email.isEmpty()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Git Configuration Missing")
                .setMessage("Please enter your GitHub Username, Personal Access Token (PAT), User Name, and User Email in Git Settings before initializing a Git repository.")
                .setPositiveButton("Open Git Settings") { _, _ ->
                    val intent = Intent(activity, GitSettingsActivity::class.java)
                    activity.startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val binding = DialogGitCloneBinding.inflate(activity.layoutInflater)
        binding.tvDialogTitle.text = "Initialize Git Repository"
        binding.btnOk.text = "Init & Push"

        val inputDialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        binding.btnCancel.setOnClickListener { inputDialog.dismiss() }

        binding.btnOk.setOnClickListener {
            val repoUrl = binding.etRepoUrl.text?.toString()?.trim() ?: ""
            var branch = binding.etBranch.text?.toString()?.trim() ?: ""

            if (repoUrl.isEmpty()) {
                SketchwareUtil.toast("Please enter a repository URL")
                return@setOnClickListener
            }

            if (branch.isEmpty()) {
                branch = "main"
            }

            inputDialog.dismiss()
            startInitAndPush(activity, scId, repoUrl, branch)
        }

        inputDialog.show()
        setupDialogWindow(activity, inputDialog)
    }

    @JvmStatic
    private fun startInitAndPush(activity: Activity, scId: String, repoUrl: String, branch: String) {
        val progressBinding = DialogGitProgressBinding.inflate(activity.layoutInflater)
        progressBinding.tvProgressTitle.text = "Publishing to GitHub..."
        progressBinding.tvProgressStatus.text = "Initializing local git repository..."

        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setView(progressBinding.root)
            .setCancelable(false)
            .create()

        var isCancelled = false

        progressBinding.btnCancelProgress.setOnClickListener {
            isCancelled = true
            progressBinding.tvProgressStatus.text = "Cancelling..."
        }

        progressDialog.show()
        setupDialogWindow(activity, progressDialog)

        thread {
            try {
                GitManager.initGitRepo(scId, branch)
                GitManager.syncProjectToGitDir(scId)
                GitManager.addRemoteOrigin(scId, repoUrl)

                activity.runOnUiThread { progressBinding.tvProgressStatus.text = "Creating initial commit..." }
                GitManager.commit(activity, scId, "Initial commit")

                activity.runOnUiThread { progressBinding.tvProgressStatus.text = "Pushing to GitHub..." }

                val monitor = object : ProgressMonitor {
                    override fun start(totalTasks: Int) {}
                    override fun beginTask(title: String, totalWork: Int) {
                        activity.runOnUiThread { progressBinding.tvProgressStatus.text = title }
                    }
                    override fun update(completed: Int) {}
                    override fun endTask() {}
                    override fun isCancelled(): Boolean = isCancelled
                }

                GitManager.forcePush(activity, scId, branch, monitor)

                activity.runOnUiThread {
                    progressDialog.dismiss()
                    SketchwareUtil.toast("Successfully published project to GitHub!")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Init & push failed: ", e)
                activity.runOnUiThread {
                    progressDialog.dismiss()
                    SketchwareUtil.toastError("Failed: ${e.message}")
                }
            }
        }
    }

    @JvmStatic
    private fun setupDialogWindow(activity: Activity, dialog: AlertDialog) {
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val screenWidth = activity.resources.displayMetrics.widthPixels
            var dialogWidth = (screenWidth * 0.85f).toInt()
            val maxDialogWidth = (420 * activity.resources.displayMetrics.density).toInt()
            if (dialogWidth > maxDialogWidth) {
                dialogWidth = maxDialogWidth
            }
            window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}
