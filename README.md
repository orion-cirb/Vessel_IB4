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

* Detect vessels with LoG + thresholding
* Detect RNA dots with DoG + thresholding
* Find RNA dots in/out dilated vessels
* Measure vessels volume + RNA dots volume and intensity in/out vessels
* If ROI(s) provided, remove from the analysis vessels and RNA dots that are inside

### Dependencies

* **3DImageSuite** Fiji plugin
* **CLIJ** Fiji plugin

### Version history

Version 1 released on January 19, 2023.

Modified on March 22, 2023:
  * Read nd/nd2 files
  * Detect RNA dots with DoG + thresholding instead of Stardist
  * Ask for RNA dots thresholding method in dialog box

Modified on June 27, 2023:
  * Corrected volume and intensity measurements given in results table
