# BetterManual

This fork includes:
1) QoS changes to make it more stable
2) Support for holy grail timelapse  - it may take a few frames to get the right exposure depending on how far off it is
3) Support for several crop modes including xpan. you can see them in the viewfinder and they write to the log with the a6000 as you can't modify exif data with the sdk.
4) Integration of the legacy lens profiles.xml from legacylens repository except it needs to be in root as PROFILES.XML it also writes to a logfile in root you can look up later
5) Staged intervalometer
6) Updates the original branch i borrowed from with support for A5100 cameras.
Tested on A6000 and A5100 specifically.

This app is intended to ease shooting in manual and aperture priority mode with (legacy) prime lenses on the A6000 camera. It uses the [OpenMemories Framework](https://github.com/ma1co/OpenMemories-Framework).
**While the app may work on other cameras, it was written specifically for use with the A6000.**

## Installation ##

Use [Sony-PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE) or install through [sony-pmca.appspot.com](https://sony-pmca.appspot.com/apps).

## Usage and features ##

Features are accessed using control wheel.

Camera Ui:
Use the Center Wheel Up/Down to cycle between the icons. Rotate the Wheel to Change the Value of the selected Item.
Use Center Wheel Left Right to toggle between GridLayout and Histogram Visibility.
Click on Ael to En/Disable FocusMagnification

Press the Picture/Play Button to open/close the Gallery.

Gallery:
Navigate with center wheel left right trough the images. 


### Focus magnification ###

use the center wheel button to toggle between different zoom factors.
Menu Button adds somekind of Crosshair, the size can get changed with the top left wheel. 
fn button adds a grid to the view
Rotate the center wheel to set focus or use your lens focus

### Other features ###

The app also includes simple timelapse and exposure bracketing modes. Click on CenterWheel Button and follow the on-screen instructions.

Long exposure noise reduction can get disabled and also SteadyShot/ImageStabilisation

Exit the app using the help/trash button.

The app remembers the (for some) configured settings across multiple runs.
