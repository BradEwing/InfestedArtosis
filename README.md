#  Infested Artosis

![](https://github.com/BradEwing/InfestedArtosis/actions/workflows/ci.yaml/badge.svg)
[![BASIL](https://img.shields.io/endpoint?url=https%3A//basil-badge-production.up.railway.app/badge/Infested%2520Artosis)](https://www.basil-ladder.net/ranking.html)

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

Run the **Release** workflow in GitHub Actions. It bumps the version in `pom.xml`, builds the jar,
creates a GitHub Release, and pushes the jar + `BWAPI.dll` zip to the BASIL ladder's Google Drive
file. See [docs/basil-cd.md](docs/basil-cd.md) for the pipeline, its security model and one-time setup.

## Thank You

- **Christian McCrave** ([McRave](https://github.com/Cmccrave)) — for providing excellent advice and code for build order, combat simulation, and micro adoption.
- **Dan Gant** ([PurpleWave](https://github.com/dgant)) — for being a great mentor on strategy, architecture, and where to go next.
