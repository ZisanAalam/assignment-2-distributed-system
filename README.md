# Distributed Weather System

A distributed weather data aggregation and retrieval system built in Java, consisting of an aggregation server, content servers, and GET clients that communicate to share and retrieve weather information.

## Project Overview

This system implements a distributed architecture for weather data management with three main components:

- **AggregationServer**: Central server that collects and aggregates weather data from multiple content servers
- **ContentServer**: Individual servers that provide weather data to the aggregation server
- **GETClient**: Client applications that retrieve weather data from the aggregation server

## Project Structure

```

├── src/main/java/
│   ├── com/weather/
│   │   ├── AggregationServer.java
│   │   ├── ContentServer.java
│   │   └── GETClient.java
│   └── utils/
│       ├── FileUtils.java
│       ├── Utils.java
│       └── WeatherData.java
├── src/test
│   └── java
│       ├── AllTest.java
│       ├── BasicRESTFunctionalityTest.java
│       └── ComprehensiveTest.java
├── target/
│   └── classes/ (compiled .class files)
├── gson-2.13.1.jar
├── Makefile
└── README.md
```

## Dependencies

### Required Libraries
- **Gson 2.13.1**: JSON parsing and serialization library
  - File: `gson-2.13.1.jar` (must be present in project root)



## Run Using Makefile

### Compile All Sources
```bash
make compile
```
or simply:
```bash
make
```

This will:
- Create the `target/classes` directory structure
- Compile all utility classes first
- Compile the weather system classes
- Include Gson in the classpath

### Clean Build Artifacts
```bash
make clean
```

## Running the Applications

### 1. Aggregation Server
```bash
make run-server ARGS="<port>"
```

Example:
```bash
make run-server ARGS="4567"
```

### 2. Content Server
```bash
make run-content ARGS="<server-url> <data-file>"
```

Example:
```bash
make run-content ARGS="http://localhost:4567 src/main/resources/vic/weather_data_1.txt"
```

### 3. GET Client
```bash
make run-client ARGS="<server-url> [station-id]"
```

Examples:
```bash
# Get all weather data
make run-client ARGS="http://localhost:4567"

# Get data for specific station
make run-client ARGS="http://localhost:4567 VIC01"
```

## Typical Usage Workflow

1. **Start the Aggregation Server**:
   ```bash
   make run-server ARGS="4567"
   ```

2. **Start Content Servers** (in separate terminals):
   ```bash
   make run-content ARGS="http://localhost:4567 src/main/resources/vic/weather_data_1.txt"
   make run-content ARGS="http://localhost:4567 src/main/resources/sa/weather_data_1.txt"
   ```

3. **Query data using GET Client**:
   ```bash
   make run-client ARGS="http://localhost:4567"
   ```

## Run Using Direct Commands

```bash
# First, compile if not already done
mkdir -p target/classes
javac -cp gson-2.13.1.jar -d target/classes -sourcepath src/main/java src/main/java/utils/*.java
javac -cp gson-2.13.1.jar:target/classes -d target/classes -sourcepath src/main/java src/main/java/com/weather/*.java

# Terminal 1: Start server
java -cp gson-2.13.1.jar:target/classes com.weather.AggregationServer 4567

# Terminal 2: Send test data
java -cp gson-2.13.1.jar:target/classes com.weather.ContentServer http://localhost:4567 src/main/resources/vic/weather_data_1.txt

# Terminal 3: Query data
java -cp gson-2.13.1.jar:target/classes com.weather.GETClient http://localhost:4567
```


