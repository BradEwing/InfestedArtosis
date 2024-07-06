#  Infested Artosis

![](https://github.com/BradEwing/InfestedArtosis/actions/workflows/ci.yaml/badge.svg)

A zerg bot initially cloned from [JavaBWAPI](https://github.com/JavaBWAPI/jbwapi-java-template).

## Features

- Opener and unit mix selections following the UCB multi-armed bandit algorithm. 
- Strong macro play
- Scouting
- Unit compositions up to lair tech supported. 

### Installation

## Command Line

Ensure that your `$JAVA_HOME` environment variable is set and pointed to Java 1.8 (I use [coretto-1.8 sdk](https://docs.aws.amazon.com/corretto/latest/corretto-8-ug/downloads-list.html))
```
$ mvn package
```


```
$ java -jar target/InfestedArtosis-{version}-jar-with-dependencies.jar
```

## Intellij (Preferred)

1. Open up this project in INtelliJ IDEA.
2. Set the Java SDK to Java 1.8. 

### Troubleshooting

Feel free to open up a GitHub issue or ping me on the [SSCAIT discord](https://discord.gg/DWHudeXmJE).

### Release

- Update version tag in `pom.xml`
- Navigate to Maven tab in Intellij, run compile then package
- Move new jar file from `target` into `sscait` dir
