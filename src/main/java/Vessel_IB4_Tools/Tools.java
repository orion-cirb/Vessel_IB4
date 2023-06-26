package Vessel_IB4_Tools;


import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.ImageIcon;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.BoundingBox;
import mcib3d.geom2.Object3DComputation;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Object3DPlane;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.VoxelInt;
import mcib3d.geom2.measurements.MeasureIntensity;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;



/**
 * @author ORION-CIRB
 */
public class Tools {
    
    private final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    private final String urlHelp = "https://github.com/orion-cirb/Vessel_IB4.git";
    private CLIJ2 clij2 = CLIJ2.getInstance();
    
    public String[] channelNames = {"Vessels", "GeneX"};
    public Calibration cal = new Calibration();
    public double pixVol = 0;
  
    // Foci
    private String fociThMethod = "Triangle";
    private double minDOGFoci = 1;
    private double maxDOGFoci = 2;
    private double minFociVol = 0.05;
    private double maxFociVol = 50;

    // Vessels
    private String vesselThMethod = "Triangle";
    private int dilVessel = 2;
    private double minVesselVol = 500;
    private double maxVesselVol = Double.MAX_VALUE;

    
    /**
     * Display a message in the ImageJ console and status bar
     */
    public void print(String log) {
        System.out.println(log);
        IJ.showStatus(log);
    }
    
    
    /**
     * Check that needed modules are installed
     */
    public boolean checkInstalledModules() {
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }
    
    
    /**
     * Find images extension
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        String[] files = imagesFolder.list();
        for (String name : files) {
            String fileExt = FilenameUtils.getExtension(name);
            switch (fileExt) {
               case "nd" :
                   ext = fileExt;
                   break;
                case "czi" :
                   ext = fileExt;
                   break;
                case "lif"  :
                    ext = fileExt;
                    break;
                case "ics" :
                    ext = fileExt;
                    break;
                case "ics2" :
                    ext = fileExt;
                    break;
                case "lsm" :
                    ext = fileExt;
                    break;
                case "tif" :
                    ext = fileExt;
                    break;
                case "tiff" :
                    ext = fileExt;
                    break;
            }
        }
        return(ext);
    }
    
    
    /**
     * Find images in folder
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    
    /**
     * Find image calibration
     */
    public void findImageCalib(IMetadata meta) {
        cal = new Calibration();
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
    }
    
    
    /**
     * Find channels name
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws loci.common.services.DependencyException, ServiceException, FormatException, IOException {
        int chs = reader.getSizeC();
        String[] channels = new String[chs];
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n);
                }
                break;
            case "nd2" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n);
                }
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n);
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelFluor(0, n);
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelExcitationWavelength(0, n).value().toString();
                break;
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelExcitationWavelength(0, n).value().toString();
                break;   
            default :
                for (int n = 0; n < chs; n++)
                    channels[n] = Integer.toString(n);
        }
        return(channels);         
    }
    
    
    /**
     * Generate dialog box
     */
    public String[] dialog(String[] chs) { 
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 100, 0);
        gd.addImage(icon);
        
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            gd.addChoice(chNames+" : ", chs, chs[index]);
            index++;
        }
        
        String[] thMethods = AutoThresholder.getMethods();
        gd.addMessage("Foci detection", Font.getFont("Monospace"), Color.blue);
        gd.addChoice("Threshold method: ",thMethods, fociThMethod);
        gd.addNumericField("Min foci volume (µm3): ", minFociVol);
        gd.addNumericField("Max foci volume (µm3): ", maxFociVol);
        
        gd.addMessage("Vessels detection", Font.getFont("Monospace"), Color.blue);
        gd.addChoice("Threshold method: ",thMethods, vesselThMethod);
        gd.addNumericField("Min vessel volume (µm3): ", minVesselVol);
        gd.addNumericField("Vessel dilation (µm): ", dilVessel);
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY pixel size (µm): ", cal.pixelWidth);
        gd.addNumericField("Z pixel size (µm): ", cal.pixelDepth);
        gd.addHelp(urlHelp);
        gd.showDialog();
        
        String[] chChoices = new String[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        
       
        fociThMethod = gd.getNextChoice();
        minFociVol = gd.getNextNumber();
        
        maxFociVol = gd.getNextNumber();
        vesselThMethod = gd.getNextChoice();
        minVesselVol = gd.getNextNumber();
        dilVessel = (int) gd.getNextNumber();
        
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelWidth*cal.pixelWidth*cal.pixelDepth;
        
        if (gd.wasCanceled())
            chChoices = null; 
        
        return(chChoices);
    }
    
    
    /**
     * Flush and close an image
     */
    public void flushCloseImg(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    /**
     * Find population of vessels
     */
    public Objects3DIntPopulation findVessels(ImagePlus imgVessel, ArrayList<Roi> rois) {
        // Detection
        ImagePlus imgLOG = new Duplicator().run(imgVessel);
        IJ.run(imgLOG, "Laplacian of Gaussian", "sigma=20 scale_normalised negate stack");
        ImagePlus imgBin = threshold(imgLOG, vesselThMethod);
        imgBin.setCalibration(cal);
        
        if (!rois.isEmpty()) {
            fillImg(imgBin, rois);
        } 
        Objects3DIntPopulation vesselsPop = getPopFromImage(imgBin);
        System.out.println("Nb vessels detected:"+vesselsPop.getNbObjects());
        
        // Size filtering
        popFilterSize(vesselsPop, minVesselVol, maxVesselVol);
        System.out.println("Nb vessels remaining after size filtering: "+ vesselsPop);
        
        flushCloseImg(imgLOG);
        flushCloseImg(imgBin);
        return(vesselsPop);
    }
    
    
    /**
     * Threshold using CLIJ2
     */
    public ImagePlus threshold(ImagePlus img, String thMed) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        ImagePlus imgBin = clij2.pull(imgCLBin);
        clij2.release(imgCL);
        clij2.release(imgCLBin);
        return(imgBin);
    }
    
        
    /**
     * Fill ROIs in black in image
     */
    private ImagePlus fillImg(ImagePlus img, ArrayList<Roi> rois) {
        img.getProcessor().setColor(Color.BLACK);
        for (int s = 1; s <= img.getNSlices(); s++) {
            img.setSlice(s);
            for (Roi r : rois) {
                img.setRoi(r);
                img.getProcessor().fill(img.getRoi());
            }
        }
        img.deleteRoi();
        return(img);
    } 
    
    
    /**
     * Return population of 3D objects population from binary image
     */
    private Objects3DIntPopulation getPopFromImage(ImagePlus img) {
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        Objects3DIntPopulation pop = new Objects3DIntPopulation(labels);
        return pop;
    } 
    
    
    /**
     * Remove objects in population with size < min and size > max
     */
    public void popFilterSize(Objects3DIntPopulation pop, double min, double max) {
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < min) || (new MeasureVolume(p).getVolumeUnit() > max));
        pop.resetLabels();
    }

    
    /**
     * Find population of geneX foci
     */
    public Objects3DIntPopulation findGenes(ImagePlus imgGene, ArrayList<Roi> rois) {
        // GeneX foci detection
        ImagePlus imgDup = new Duplicator().run(imgGene);
        ImagePlus imgDOG = DOG(imgDup, minDOGFoci, maxDOGFoci);
        ImagePlus imgBin = threshold(imgDOG, fociThMethod);
        imgBin.setCalibration(cal);
        
        if (!rois.isEmpty()) {
            fillImg(imgBin, rois);
        }
        Objects3DIntPopulation genesPop = getPopFromImage(imgBin);
        System.out.println("Nb geneX foci detected:"+genesPop.getNbObjects());
        
        // Size filtering
        popFilterSize(genesPop, minFociVol, maxFociVol);
        System.out.println("Nb geneX foci remaining after size filtering: "+ genesPop);
        
        flushCloseImg(imgDup);
        flushCloseImg(imgDOG);
        flushCloseImg(imgBin);
        return(genesPop);
    }
     
    
    /**
     * Difference of Gaussians with CLIJ2
     */ 
    public ImagePlus DOG(ImagePlus img, double size1, double size2) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, size1, size1, size1, size2, size2, size2);
        ImagePlus imgDOG = clij2.pull(imgCLDOG);
        clij2.release(imgCL);
        clij2.release(imgCLDOG);
        return(imgDOG);
    }
    
    
    /**
     * Find geneX dots into and out of vessels
     */
    public List<Objects3DIntPopulation> findGeneXInOutVessels(Objects3DIntPopulation geneXPop, Objects3DIntPopulation vesselsPop, ImagePlus imgGeneX) {
        ImageHandler imh = ImageHandler.wrap(imgGeneX).createSameDimensions();
        geneXPop.drawInImage(imh);
        ImageHandler imhDup = imh.duplicate();
        
        for (Object3DInt vessel: vesselsPop.getObjects3DInt()) {
            Object3DInt vesselDil = dilateObj(vessel, imgGeneX, dilVessel);
            vesselDil.drawObject(imh, 0);
        }

        Objects3DIntPopulation popOut = new Objects3DIntPopulation(imh);
        
        ImagePlus imgSub = new ImageCalculator().run("subtract stack create", imhDup.getImagePlus(), imh.getImagePlus());
        Objects3DIntPopulation popIn = new Objects3DIntPopulation(ImageHandler.wrap(imgSub));
        
        imh.closeImagePlus();
        imhDup.closeImagePlus();
        return(Arrays.asList(popIn, popOut));  
    }

    
    /**
     * Return dilated object restricted to image borders
     */
    public Object3DInt dilateObj(Object3DInt obj, ImagePlus img, double dilSize) {
        Object3DInt objDil = new Object3DComputation(obj).getObjectDilated((float)(dilSize/cal.pixelWidth), (float)(dilSize/cal.pixelHeight),(float)(dilSize/cal.pixelDepth));
        
        // Check if object goes over image borders
        BoundingBox bbox = objDil.getBoundingBox();
        BoundingBox imgBbox = new BoundingBox(ImageHandler.wrap(img));
        int[] box = {imgBbox.xmin, imgBbox.xmax, imgBbox.ymin, imgBbox.ymax, imgBbox.zmin, imgBbox.zmax};
        if (bbox.xmin < 0 || bbox.xmax > imgBbox.xmax || bbox.ymin < 0 || bbox.ymax > imgBbox.ymax || bbox.zmin < 0 || bbox.zmax > imgBbox.zmax) {
            Object3DInt objDilInImg = new Object3DInt();
            for (Object3DPlane p: objDil.getObject3DPlanes()) {
                for (VoxelInt v: p.getVoxels()) {
                    if (v.isInsideBoundingBox(box))
                        objDilInImg.addVoxel(v);
                }
            }
            return(objDilInImg);
        } else {
            return(objDil);
        }
    }

  
    /**
     * Draw results
     */
    public void drawResults(ImagePlus imgGeneX, Objects3DIntPopulation vesselsPop, Objects3DIntPopulation genesXIn, Objects3DIntPopulation genesXOut,
            String outDirResults, String rootName) {
        ImageHandler imgVessels = ImageHandler.wrap(imgGeneX).createSameDimensions();
        ImageHandler imgGeneXIn = ImageHandler.wrap(imgGeneX).createSameDimensions();
        ImageHandler imgGeneXOut = ImageHandler.wrap(imgGeneX).createSameDimensions();
        
        // Draw vessels pop in blue, geneXIn pop in red and geneXOut pop in green
        for (Object3DInt vessel: vesselsPop.getObjects3DInt()) 
                vessel.drawObject(imgVessels, 255);
        for (Object3DInt ob : genesXIn.getObjects3DInt()) 
                ob.drawObject(imgGeneXIn, 255);
        for (Object3DInt ob : genesXOut.getObjects3DInt()) 
                ob.drawObject(imgGeneXOut, 255);
        ImagePlus[] imgColors = {imgGeneXIn.getImagePlus(), imgGeneXOut.getImagePlus(), imgVessels.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save resulting image
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + ".tif");
        
        imgVessels.closeImagePlus();
        imgGeneXIn.closeImagePlus();
        imgGeneXOut.closeImagePlus();
    }
    
    
    /**
     * Write results
     */
    public void writeResults(BufferedWriter results, Objects3DIntPopulation vesselsPop, Objects3DIntPopulation genesXIn, Objects3DIntPopulation genesXOut, 
            ImagePlus imgGeneX, String imgName) throws IOException {
        double bg = findBackground(imgGeneX);
        double vesselsVol = findPopVolume(vesselsPop);
        double genesXInVol = findPopVolume(genesXIn);
        double genesXInInt = findPopIntensity(genesXIn, imgGeneX);
        double genesXInIntBgCor = genesXInInt - bg/pixVol;
        double genesXOutVol = findPopVolume(genesXOut);
        double genesXOutInt = findPopIntensity(genesXOut, imgGeneX);
        double genesXOutIntBgCor = genesXInInt - bg/pixVol;
        
        results.write(imgName+"\t"+bg+"\t"+vesselsVol+"\t"+genesXInVol+"\t"+genesXInInt+"\t"+genesXInIntBgCor+"\t"+
                      genesXOutVol+"\t"+genesXOutInt+"\t"+genesXOutIntBgCor+"\n");
        results.flush();
    }
    
    
    /**
     * Find image background intensity:
     * Perform zrojection over min intensity + read median intensity
     */
    public double findBackground(ImagePlus img) {
      ImagePlus imgProj = doZProjection(img, ZProjector.MIN_METHOD);
      double bg = imgProj.getProcessor().getStatistics().median;
      System.out.println("Background (median of the min projection) = " + bg);
      
      flushCloseImg(imgProj);
      return(bg);
    }
    
                  
    /**
     * Do Z-projection
     */
    public ImagePlus doZProjection(ImagePlus img, int param) {
        ZProjector zproject = new ZProjector();
        zproject.setMethod(param);
        zproject.setStartSlice(1);
        zproject.setStopSlice(img.getNSlices());
        zproject.setImage(img);
        zproject.doProjection();
       return(zproject.getProjection());
    }
   
   
    /**
     * Find total volume of objects in population
     */
    public double findPopVolume(Objects3DIntPopulation pop) {
        double totalVol = 0;
        for(Object3DInt obj: pop.getObjects3DInt())
            totalVol += new MeasureVolume(obj).getVolumeUnit();
        return(totalVol);
    }
    
    
    /**
     * Find total intensity of objects in population
     */
    public double findPopIntensity(Objects3DIntPopulation pop, ImagePlus img) {
        ImageHandler imh = ImageHandler.wrap(img);
        double totalInt = 0;
        for(Object3DInt obj: pop.getObjects3DInt())
            totalInt +=  new MeasureIntensity(obj, imh).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
        return(totalInt);
    }
 
}
