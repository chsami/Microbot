MVN=mvn
MVN_FLAGS=-am
MVN_FILE=./pom.xml

JAVA=java
OSX_FULLSCREEN_FLAG=-ea --add-opens java.desktop/com.apple.eawt=ALL-UNNAMED
JAVA_FLAGS=-Xmx1024M

TARGET_JAR=$(shell fd -t f -g "microbot-*.jar" "./runelite-client/target" | sort -V | head -n1)


.PHONY: bin_check reset clean package compile recompile-client run all test

bin_check:
	[ -r $(TARGET_JAR) ] || echo "Microbot client jar file not found" && exit 1

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
	JAVA_HOME=$(JAVA_HOME) $(JAVA) $(JAVA_FLAGS) $(OSX_FULLSCREEN_FLAG) -jar $(TARGET_JAR)

all: clean compile bin_check run
