# HistoFlow

Cancer detection AI platform, inspired by Lunit.

## Setup

### Backend

Kotlin + Spring Boot

#### 1. Install SDKMAN and Java

```bash
# install SDKMAN
curl -s "https://get.sdkman.io" | bash

# Source the SDKMAN initialization script
source "$HOME/.sdkman/bin/sdkman-init.sh"

# verify installation
sdk version

# list available Java versions
sdk list java

# Install OpenJDK 17 (Temurin distribution)
sdk install java 17.0.12-tem

# set as default version
sdk default java 17.0.12-tem

# verify installation
java -version
```

#### Intellij Community Edition Setup

Download Intellij Community Edition from JetBrains. Since the community edition does not allow for starting a new Spring Boot project, we'll have to use [https://start.spring.io/](https://start.spring.io/) to initialize the project and open it in the Intellij.

#### Kotlin + Spring Boot Project Setup

[screenshot](./assets/Screenshot 2025-09-27 at 22-15-43 Spring Initializr.png)

Then, you can open the backend directory in Intellij Community.

#### Install Kotlin Compiler for Commandline use

```bash
# sdk install
sdk install kotlin

# or, can use brew
brew install kotlin
```
