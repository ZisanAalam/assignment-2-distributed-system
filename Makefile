# Makefile for Distributed Weather System
# Variables
JAVA_SRC_DIR = src/main/java
TARGET_DIR = target
CLASSES_DIR = $(TARGET_DIR)/classes

# Java files
WEATHER_SOURCES = $(wildcard $(JAVA_SRC_DIR)/com/weather/*.java)
UTILS_SOURCES = $(wildcard $(JAVA_SRC_DIR)/utils/*.java)
ALL_SOURCES = $(WEATHER_SOURCES) $(UTILS_SOURCES)

# Class files
WEATHER_CLASSES = $(WEATHER_SOURCES:$(JAVA_SRC_DIR)/%.java=$(CLASSES_DIR)/%.class)
UTILS_CLASSES = $(UTILS_SOURCES:$(JAVA_SRC_DIR)/%.java=$(CLASSES_DIR)/%.class)
ALL_CLASSES = $(ALL_SOURCES:$(JAVA_SRC_DIR)/%.java=$(CLASSES_DIR)/%.class)

# External libraries
GSON_JAR = gson-2.13.1.jar
CLASSPATH = $(GSON_JAR)

# Java compiler flags
JAVAC = javac
JAVAC_FLAGS = -cp $(CLASSPATH) -d $(CLASSES_DIR) -sourcepath $(JAVA_SRC_DIR)

# Java runtime flags
JAVA = java
JAVA_FLAGS = -cp $(CLASSPATH):$(CLASSES_DIR)

# Default target
.PHONY: all
all: compile

# Create necessary directories
$(CLASSES_DIR):
	mkdir -p $(CLASSES_DIR)

# Compile all Java sources
.PHONY: compile
compile: $(CLASSES_DIR) $(ALL_CLASSES)

# Compile utils classes first (dependencies)
$(CLASSES_DIR)/utils/%.class: $(JAVA_SRC_DIR)/utils/%.java | $(CLASSES_DIR)
	$(JAVAC) $(JAVAC_FLAGS) $<

# Compile weather classes (depends on utils)
$(CLASSES_DIR)/com/weather/%.class: $(JAVA_SRC_DIR)/com/weather/%.java $(UTILS_CLASSES) | $(CLASSES_DIR)
	$(JAVAC) $(JAVAC_FLAGS) $<

# Run individual applications
.PHONY: run-server
run-server: compile
	$(JAVA) $(JAVA_FLAGS) com.weather.AggregationServer $(ARGS)

.PHONY: run-content
run-content: compile
	$(JAVA) $(JAVA_FLAGS) com.weather.ContentServer $(ARGS)

.PHONY: run-client
run-client: compile
	$(JAVA) $(JAVA_FLAGS) com.weather.GETClient $(ARGS)

# Clean compiled files
.PHONY: clean
clean:
	rm -rf $(TARGET_DIR)