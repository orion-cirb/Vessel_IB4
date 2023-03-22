# Vessel_IB4

* **Developed for:** Katia
* **Team:** Cohen-Salmon
* **Date:** January 2023
* **Software:** Fiji

### Images description

3D images.

2 channels:
  1. *Alexa Fluor 561:* RNA dots
  2. *Alexa Fluor 642:* IB4 vessels

### Plugin description

* if roi(s) exist fill roi(s) with zero
* Detect vessels with LoG + thresholding
* Detect RNA dots with DOG and threshold
* Find RNA in/out dilated vessels
* Measure vessels volume + RNA dots volume and intensity in/out vessels

### Dependencies

* **3DImageSuite** Fiji plugin
* **CLIJ** Fiji plugin

### Version history

Version 1 released on January 12, 2023.
Modified 22 mars 2023 to :
  * read nd/nd2
  * Ask for foci thresholding method
