# simulated.Infrared Detector

This project implements an HCD (Hardware Control Daemon) and an Assembly using
TMT Common Software ([CSW](https://github.com/tmtsoftware/csw)) APIs.

## Subprojects

* simulated-infrared-detector - an assembly that talks to the simulated.Infrared Detector HCD
* simulated-infrared-detectorhcd - an HCD that talks to the simulated.Infrared Detector hardware


## Building the HCD and Assembly Applications

* Run `sbt simulated-infrared-detector-deploy/universal:packageBin`, this will create self-contained zip in `simulated-infrared-detector-deploy/target/universal` directory
* Unzip the generated zip and cd into the bin directory

Note: An alternative method is to run `sbt stage`, which installs the applications locally in `simulated-infrared-detector-deploy/target/universal/stage/bin`.

## Running the HCD and Assembly

Run the container cmd script with arguments. For example:

* Run the HCD in a standalone mode with a local config file (The standalone config format is different than the container format):

```
./target/universal/stage/bin/simulatedInfraredDetector-container-cmd-app --standalone --local ./src/main/resources/SampleHcdStandalone.conf
```

* Start the HCD and assembly in a container using the Java implementations:

```
./target/universal/stage/bin/simulatedInfraredDetector-container-cmd-app --local ./src/main/resources/JSampleContainer.conf
```

* Or the Scala versions:

```
./target/universal/stage/bin/simulatedInfraredDetector-container-cmd-app --local ./src/main/resources/SampleContainer.conf
```
