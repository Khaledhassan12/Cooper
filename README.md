<p align="center">
  <img src="assets/Sketchware-DayGreen.png" width="128" alt="Cooper's App Icon">
</p>

<h1 align="center">Cooper's</h1>

<p align="center">
  <b>A customizable Android IDE and visual app builder</b>
</p>

<p align="center">
<a href="https://github.com/Khaledhassan12/Cooper/blob/Cooper-integrated-github/LICENSE">
  <img src="https://img.shields.io/badge/license-Apache--2.0-007ec6" alt="License">
</a>
  <img src="https://img.shields.io/badge/platform-Android-4c1" alt="Platform">
  <a href="https://github.com/Khaledhassan12/Cooper/releases">
    <img src="https://img.shields.io/github/downloads/Khaledhassan12/Cooper/total?color=dfb317" alt="Downloads">
  </a>
  <img src="https://img.shields.io/github/repo-size/Khaledhassan12/Cooper" alt="Repo Size">
</p>

<p align="center">
  <a href="https://t.me/sketchware_daygreen">
    <img src="https://img.shields.io/badge/Telegram-26A5E4?logo=telegram&logoColor=white" alt="Telegram">
  </a>
  <a href="https://discord.gg/V4ePcgaCq">
    <img src="https://img.shields.io/badge/Discord-5865F2?logo=discord&logoColor=white" alt="Discord">
  </a>
</p>

# Important!
Cooper builds on an established open-source Android visual app builder and incorporates selected upstream improvements. See the [upstream source repository](https://github.com/Sketchware-Pro/Sketchware-Pro). Please back up your projects before using this version.

# Cooper's

Welcome to Cooper's! This repository contains the source code for Cooper's Android IDE and is the place to contribute improvements, fixes, and new capabilities.

## Building the App
To build the app, you must use Gradle. It's highly recommended to use Android Studio for the best experience.

### Source Code Map

| Class           | Role                                        |
| --------------- | ------------------------------------------- |
| `a.a.a.ProjectBuilder`      | Helper for compiling an entire project       |
| `a.a.a.Ix`      | Responsible for generating AndroidManifest.xml |
| `a.a.a.Jx`      | Generates source code of activities          |
| `a.a.a.Lx`      | Generates source code of components, such as listeners, etc. |
| `a.a.a.Ox`      | Responsible for generating XML files of layouts |
| `a.a.a.qq`      | Registry of built-in libraries' dependencies |
| `a.a.a.tq`      | Responsible for the compiling dialog's quizzes |
| `a.a.a.yq`      | Organizes Sketchware projects' file paths    |

> [!TIP]
> You can also check the `mod` package, which contains the majority of contributors' changes.

## Contributing

If you'd like to contribute to Cooper's, follow these steps:

1. Fork this repository.
2. Make changes in your forked repository.
3. Test out those changes.
4. Create a pull request in this repository.
5. Your pull request will be reviewed by the repository members and merged if accepted.

We welcome contributions of any size, whether they are major features or bug fixes, but please note that all contributions will be thoroughly reviewed.

### Commit Message

When you make changes to one or more files, you need to commit those changes with a commit message. Here are some guidelines:

- Keep the commit message short and detailed.

> [!IMPORTANT]
> If you want to add new features that don't require editing other packages other than `pro.sketchware`, make your changes in `pro.sketchware` package, and respect the directories and files structure and names. Also, even though the project compiles just fine with Kotlin classes that you might add, try to make your changes or additions in Java, not Kotlin unless it is more than necessary.

## Thanks for Contributing

Thank you for contributing to Cooper's! Your contributions help improve the IDE and benefit its users. Each accepted contribution will be noted down in the "About Team" activity. We'll use your GitHub name and profile picture initially, but they can be changed, of course.

## Disclaimer

This mod was not created for any harmful purposes, such as harming Sketchware; quite the opposite, actually. It was made to keep Sketchware alive by the community for the community. Please use it at your own discretion.

Before redistributing Cooper's, review this repository's license and all applicable upstream licenses. Do not represent Cooper's as the original upstream project.

Cooper's is intended to keep an approachable visual Android development experience useful and extensible for the community. It is free to use; contributions and bug reports are welcome.