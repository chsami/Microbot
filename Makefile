MVN=mvn
MVN_FLAGS=-am
MVN_FILE=./pom.xml

JAVA=java
JAVA_HOME="/Users/harry/java/jdk-17.0.14+7/Contents/Home"
MEMORY="512M"
TARGET_JAR=./runelite-client/target/microbot-1.7.9.jar

.PHONY: reset clean package compile recompile-client run all

reset:
	JAVA_HOME=$(JAVA_HOME) $(MVN) $(MVN_FLAGS) -f $(MVN_FILE) clean package compile install

clean:
	JAVA_HOME=$(JAVA_HOME) $(MVN) $(MVN_FLAGS) -f $(MVN_FILE) clean

package:
	JAVA_HOME=$(JAVA_HOME) $(MVN) $(MVN_FLAGS) -f $(MVN_FILE) package

compile:
	JAVA_HOME=$(JAVA_HOME) $(MVN) $(MVN_FLAGS) -f $(MVN_FILE) compile install

recompile-client:
	JAVA_HOME=$(JAVA_HOME) $(MVN) $(MVN_FLAGS) -f $(MVN_FILE) compile install -rf :client

run:
	JAVA_HOME=$(JAVA_HOME) $(JAVA) -Xmx$(MEMORY) -jar $(TARGET_JAR)

all: reset compile run
