# source me: throttled Gradle environment for Nebulos on this Mac (cold builds once froze it)
export JAVA_HOME=/Users/ry/android-dev/jdk-17.0.20.1+1/Contents/Home ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$HOME/android-dev/bin:$HOME/.cargo/bin:$PATH GRADLE_OPTS=-Xmx3g CMAKE_BUILD_PARALLEL_LEVEL=4
