<div align="center">

![image](https://github.com/user-attachments/assets/f63dcb2c-5208-4d2a-8b6f-7b95efa56d36)

  [![Release](https://img.shields.io/github/release/serifpersia/midi-router.svg?style=flat-square)](https://github.com/serifpersia/midi-router/releases)
  [![License](https://img.shields.io/github/license/serifpersia/midi-router?color=blue&style=flat-square)](https://raw.githubusercontent.com/serifpersia/midi-router/master/LICENSE)
</div>

## MIDI Router
MIDI Router is a straightforward Java application designed for MIDI routing.

## Dependencies
To run jar or exe file you need [Java JRE](https://adoptium.net/temurin/releases/?os=windows&arch=aarch64&package=jre&version=17) installed before you can launch MIDI Router
## Usage

1. **Virtual MIDI Devices:**
   - To route a single MIDI device to two virtual MIDI devices, it is recommended to use loopMIDI
   - Follow the steps below to set up virtual MIDI devices using loopMIDI.

2. **Setup with loopMIDI:**
   - Download and install loopMIDI from [here](https://www.tobias-erichsen.de/software/loopmidi.html).
   - Open loopMIDI and follow the image below to create the virtual MIDI devices.

![image](https://github.com/serifpersia/midi-router/assets/62844718/822d4d26-7a22-494c-af21-ff94b42f22ba)

3. **MIDI Router Application:**
   - Launch the MIDI Router application.
   - Click on MIDI IN device you want to route MIDI data from and drag to a MIDI OUT device/s that will receive the data.
   - Click on white circle in the middle of the connection to stop routing.

## Download
  [![Download](https://img.shields.io/github/release/serifpersia/midi-router.svg?style=flat-square)](https://github.com/serifpersia/midi-router/releases)
