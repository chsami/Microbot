MVN=$(shell which mvn)
MVN_FILE=$(CURDIR)/pom.xml
MVN_FLAGS=-am -cpu

JAVA=$(shell which java)
JAVA_FLAGS=-Xmx2048m -XX:+IgnoreUnrecognizedVMOptions
OSX_FULLSCREEN_FLAG=-ea --add-opens='java.desktop/com.apple.eawt=ALL-UNNAMED'

FIND=$(shell which find)
TARGET_JAR=$(shell $(FIND) $(CURDIR)/runelite-client/target -type f -name "microbot-*.jar" -print | sort -V | head -n1)

HOTSWAP_JAR_PATH=$(JAVA_HOME)/lib/hotswap/hotswap-agent.jar
HOTSWAP_FLAGS=-javapath:$(HOTSWAP_JAR_PATH)=autoHotswap=true -XX:+AllowEnhancedClassRedefinition -XX:HotswapAgent=core -XX:HotswapAgent=fatjar -Xlog:redefine+class*=info

.PHONY: check bin_check hot_check reset clean package compile recompile-client all test run

check:
	@[ -x "$(MVN)" ] || { echo "Cannot find mvn executable in path"; exit 1; }
	@[ -x "$(JAVA)" ] || { echo "Cannot find java executable in path"; exit 1; }
	@[ -x "$(FIND)" ] || { echo "Cannot find fd executable in path"; exit 1; }
	@[ -r "$(MVN_FILE)" ] || { echo "Unable to locate maven pom.xml file"; exit 1; }
	@[ -d "./runelite-client/src/main/java/net/runelite/client/plugins/microbot" ] || { echo "Microbot plugin SDK not found"; exit 1; }

bin_check:
	@[ -r "$(TARGET_JAR)" ] || { echo "Microbot client jar file not found"; exit 1; }

hot_check:
	@[ -r $(HOTSWAP_JAR_PATH) ] || { echo "HotSwapAgent path to jar not found"; echo "Usage: make -e HOTSWAP_JAR_PATH=YOUR_HOTSWAPAGENT_JAR_PATH <target>"; exit 1; }

reset:
	$(MVN) $(MVN_FLAGS) -f $(MVN_FILE) clean package compile install

clean: check
	$(MVN) $(MVN_FLAGS) -f $(MVN_FILE) clean

package: check
	$(MVN) $(MVN_FLAGS) -f $(MVN_FILE) package

compile: check
	$(MVN) $(MVN_FLAGS) -f $(MVN_FILE) compile

recompile_client: check
	$(MVN) $(MVN_FLAGS) -f $(MVN_FILE) compile -rf :client

install: check
	$(MVN) $(MVN_FLAGS) -f $(MVN_FILE) install

run: bin_check
	$(JAVA) $(JAVA_FLAGS) $(if $(findstring Darwin,$(shell uname)), \
        $(OSX_FULLSCREEN_FLAG), \
		:; true \
    ) -jar $(TARGET_JAR)

run_hotswapable: bin_check hot_check
	$(JAVA) $(JAVA_FLAGS) $(if $(findstring Darwin,$(shell uname)), \
        $(OSX_FULLSCREEN_FLAG), \
		:; true \
    ) $(HOTSWAP_FLAGS) -jar $(TARGET_JAR)

test: check
	$(MVN) $(MVN_FLAGS) -f $(MVN_FILE) test

all:
	@echo "Ensuring dependencies are present..." && $(MAKE) check > /dev/null 2>&1
	@echo "Cleaning, packaging, compiling and installing project..." && $(MAKE) reset > /dev/null 2>&1
	@echo "Running tests..." && $(MAKE) test > /dev/null 2>&1
	@echo "Executing latest freshly built dev client..." && [! -r $(HOTSWAP_JAR_PATH) ] && $(MAKE) run || $(MAKE) run_hotswapable
