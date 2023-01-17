package Vessel_IB4_Utils;



import Vessel_IB4_StardistOrion.StarDist2D;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.plugin.filter.Analyzer;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.ImageIcon;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Point3D;
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
 *
 * @author phm
 */

public class Vessel_Processing {
    
    //Stardist
    private Object syncObject = new Object();
    private final double stardistPercentileBottom = 0.2;
    private final double stardistPercentileTop = 99.8;
    private final double stardistProbThresh = 0.5;
    private final double stardistOverlayThresh = 0.25;
    private final File stardistModelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    public String stardistFociModel = "fociRNA-1.2.zip";
    private String stardistOutput = "Label Image"; 
    
    public int removeSlice = 0;
    public int dilVessel = 2;
    private String thMethod = "";
    public double minVesselVol = 5000, maxVesselVol = Double.MAX_VALUE;;
    public Calibration cal = new Calibration();
    private double pixVol = 0;

    
    private CLIJ2 clij2 = CLIJ2.getInstance();
     /**
     * check  installed modules
     * @return 
     */
    public boolean checkInstalledModules() {
        // check install
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
     * @param imagesFolder
     * @return 
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        File[] files = imagesFolder.listFiles();
        for (File file: files) {
            if(file.isFile()) {
                String fileExt = FilenameUtils.getExtension(file.getName());
                switch (fileExt) {
                   case "nd" :
                       ext = fileExt;
                       break;
                   case "nd2" :
                       ext = fileExt;
                       break;
                    case "czi" :
                       ext = fileExt;
                       break;
                    case "lif"  :
                        ext = fileExt;
                        break;
                    case "ics2" :
                        ext = fileExt;
                        break;
                    case "tif" :
                        ext = fileExt;
                        break;
                    case "tiff" :
                        ext = fileExt;
                        break;
                }
            } else if (file.isDirectory() && !file.getName().equals("Results")) {
                ext = findImageType(file);
                if (! ext.equals(""))
                    break;
            }
        }
        return(ext);
    }
    
   /**
     * Find images in folder
     * @param imagesFolder
     * @param imageExt
     * @return 
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
     * @param meta
     * @return 
     */
    public Calibration findImageCalib(IMetadata meta) {
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
        return(cal);
    }
    
    
     /**
     * Find channels name
     * @param imageName
     * @return 
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        ArrayList<String> channels = new ArrayList<>();
        int chs = reader.getSizeC();
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                }
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelFluor(0, n).toString());
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break; 
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break;        
            default :
                for (int n = 0; n < chs; n++)
                    channels.add(Integer.toString(n));

        }
        channels.add("None");
        return(channels.toArray(new String[channels.size()]));         
    }
    
    
     /**
     * Generate dialog box
     */
    public String[] dialog(String[] chs) { 
        ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
        String[] channelNames = {"Vessel", "GeneX"};
        String[] thMethods = AutoThresholder.getMethods();
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 100, 0);
        gd.addImage(icon);
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            gd.addChoice(chNames+" : ", chs, chs[index]);
            index++;
        }
        
        gd.addMessage("Vessel detection", Font.getFont("Monospace"), Color.blue);
        gd.addChoice("Threshold method : ",thMethods, thMethods[10]);
        gd.addNumericField("Min vessel volume (µm3): ", minVesselVol);
        gd.addNumericField("Max vessel volume (µm3): ", maxVesselVol);
        gd.addNumericField("Vessel dilatation (µm): ", dilVessel);
        gd.addNumericField("Slice to remove : ", removeSlice);
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY pixel size (µm): ", cal.pixelWidth);
        gd.addNumericField("Z pixel size (µm): ", cal.pixelDepth);
        gd.showDialog();
        
        String[] chChoices = new String[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        if (gd.wasCanceled())
            chChoices = null;        
        thMethod = gd.getNextChoice();
        minVesselVol = gd.getNextNumber();
        maxVesselVol = gd.getNextNumber();
        dilVessel = (int)gd.getNextNumber();
        removeSlice = (int)gd.getNextNumber();
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelWidth*cal.pixelWidth*cal.pixelDepth;
        return(chChoices);
    }
    
    
    /**
     *
     * @param img
     */
    public void closeImages(ImagePlus img) {
        img.flush();
        img.close();
    }

    
  /**
     * return objects population in an binary image
     * @param img
     * @return pop
     */

    private Objects3DIntPopulation getPopFromImage(ImagePlus img) {
        // label binary images first
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        Objects3DIntPopulation pop = new Objects3DIntPopulation(labels);
        return pop;
    } 
    
    
    /**
     * gaussian 3D filter 
     * Using CLIJ2
     * @param imgCL
     * @param sizeX
     * @param sizeY
     * @param sizeZ
     * @return imgOut
     */
 
    public ClearCLBuffer gaussianBlur3D(ClearCLBuffer imgCL, double sizeX, double sizeY, double sizeZ) {
        ClearCLBuffer imgOut = clij2.create(imgCL);
        clij2.gaussianBlur3D(imgCL, imgOut, sizeX, sizeY, sizeZ);
        clij2.release(imgCL);
        return(imgOut);
    }
    
     /**  
     * median 3D box filter
     * Using CLIJ2
     * @param imgCL
     * @param sizeX
     * @param sizeY
     * @param sizeZ
     * @return imgOut
     */ 
    public ClearCLBuffer medianFilter(ClearCLBuffer imgCL, double sizeX, double sizeY, double sizeZ) {
        ClearCLBuffer imgOut = clij2.create(imgCL);
        clij2.median3DBox(imgCL, imgOut, sizeX, sizeY, sizeZ);
        clij2.release(imgCL);
        return(imgOut);
    }
    
    /**
     * Difference of Gaussians 
     * Using CLIJ2
     * @param imgCL
     * @param sizeX1
     * @param sizeY1
     * @param sizeZ1
     * @param sizeX2
     * @param sizeY2
     * @param sizeZ2
     * @return imgGauss
     */ 
    public ClearCLBuffer DOG(ClearCLBuffer imgCL, double sizeX1, double sizeY1, double sizeZ1, double sizeX2, double sizeY2, double sizeZ2) {
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, sizeX1, sizeY1, sizeZ1, sizeX2, sizeY2, sizeZ2);
        clij2.release(imgCL);
        return(imgCLDOG);
    }
    
    /**
     * Fill hole
     * USING CLIJ2
     */
    private void fillHole(ClearCLBuffer imgCL) {
        long[] dims = clij2.getDimensions(imgCL);
        ClearCLBuffer slice = clij2.create(dims[0], dims[1]);
        ClearCLBuffer slice_filled = clij2.create(slice);
        for (int z = 0; z < dims[2]; z++) {
            clij2.copySlice(imgCL, slice, z);
            clij2.binaryFillHoles(slice, slice_filled);
            clij2.copySlice(slice_filled, imgCL, z);
        }
        clij2.release(imgCL);
        clij2.release(slice);
        clij2.release(slice_filled);
    }
    
    /**
     * Threshold 
     * USING CLIJ2
     * @param imgCL
     * @param thMed
     * @param fill 
     */
    public ClearCLBuffer threshold(ClearCLBuffer imgCL, String thMed, boolean fill) {
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        if (fill)
            fillHole(imgCLBin);
        clij2.release(imgCL);
        return(imgCLBin);
    }
    
    /**
     * Fill rois with zero
     */
    private ClearCLBuffer fillImg(ClearCLBuffer imgCL, ArrayList<Roi> rois) {
        ImagePlus img = clij2.pull(imgCL);
        img.getProcessor().setColor(Color.BLACK);
        for (int s = 1; s <= img.getNSlices(); s++) {
            img.setSlice(s);
            for (Roi r : rois) {
                img.setRoi(r);
                img.getProcessor().fill(img.getRoi());
            }
        }
        img.deleteRoi();
        ClearCLBuffer imgCLOut = clij2.push(img);
        closeImages(img);
        clij2.release(imgCL);
        return(imgCLOut);
    } 
    
    /**
     * Ib4cyte Cell
     * 
     */
    private Objects3DIntPopulation detectIb4(ImagePlus imgVessel, ArrayList<Roi> rois) {
        ImagePlus imgLOG = new Duplicator().run(imgVessel);
        IJ.run(imgLOG, "Laplacian of Gaussian", "sigma=20 scale_normalised negate stack");
        ClearCLBuffer imgCLLOG = clij2.push(imgLOG);
        ClearCLBuffer imgCLBin = threshold(imgCLLOG, thMethod, false);
        Objects3DIntPopulation ib4Pop = new Objects3DIntPopulation();
        if (!rois.isEmpty()) {
            ClearCLBuffer fillImg = fillImg(imgCLBin, rois);
            ib4Pop = getPopFromImage(clij2.pull(fillImg));
            clij2.release(fillImg);
        }
        else 
            ib4Pop = getPopFromImage(clij2.pull(imgCLBin));
        clij2.release(imgCLBin);
        return(ib4Pop);
    }
    

    /**
     * Remove object with size < min and size > max
     * @param pop
     * @param min
     * @param max
     */
    public void popFilterSize(Objects3DIntPopulation pop, double min, double max) {
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < min) || (new MeasureVolume(p).getVolumeUnit() > max));
        pop.resetLabels();
    }
    
     /**
     * Find sum volume of objects  
     * @param dotsPop
     * @return vol
     */
    
    public double findPopVolume (Objects3DIntPopulation dotsPop) {
        IJ.showStatus("Findind object's volume");
        List<Double[]> results = dotsPop.getMeasurementsList(new MeasureVolume().getNamesMeasurement());
        double sum = results.stream().map(arr -> arr[1]).reduce(0.0, Double::sum);
        return(sum);
    }
    
     /**
     * Find sum intensity of objects  
     * @param dotsPop
     * @param img
     * @return intensity
     */
    
    public double findPopIntensity (Objects3DIntPopulation dotsPop, ImagePlus img) {
        IJ.showStatus("Findind object's intensity");
        ImageHandler imh = ImageHandler.wrap(img);
        double sumInt = 0;
        for(Object3DInt obj : dotsPop.getObjects3DInt()) {
            MeasureIntensity intMes = new MeasureIntensity(obj, imh);
            sumInt +=  intMes.getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
        }
        return(sumInt);
    }
    
    
    public  Objects3DIntPopulation findVessel(ImagePlus imgVessel, ArrayList<Roi> rois) {
        Objects3DIntPopulation VesselPopOrg = detectIb4(imgVessel, rois);
        System.out.println("-- Total Vessel Population :"+VesselPopOrg.getNbObjects());
        // size filter
        popFilterSize(VesselPopOrg, minVesselVol, maxVesselVol);
        int nbCellPop = VesselPopOrg.getNbObjects();
        System.out.println("-- Total Vessel Population after size filter: "+ nbCellPop);
        return(VesselPopOrg);
    }
    
     /**
     * Do Z projection
     * @param img
     * @param param
     * @return 
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
     * Find background image intensity:
     * Z projection over min intensity + read median intensity
     * @param img
     */
    public double findBackground(ImagePlus img) {
      ImagePlus imgProj = doZProjection(img, ZProjector.MIN_METHOD);
      ImageProcessor imp = imgProj.getProcessor();
      double bgGene = imp.getStatistics().median;
      System.out.println("Background (median of the min projection) = " + bgGene);
      closeImages(imgProj);
      return(bgGene);
    }
   
    
     /**
     * Return dilated object restriced to image borders
     * @param img
     * @param obj
     * @param dilSize
     * @return 
     */
    public Object3DInt dilateObj(ImagePlus img, Object3DInt obj, double dilSize) {
        Object3DInt objDil = new Object3DComputation(obj).getObjectDilated((float)(dilSize/cal.pixelWidth), (float)(dilSize/cal.pixelHeight), 
                (float)(dilSize/cal.pixelDepth));
        // check if object go outside image
        BoundingBox bbox = objDil.getBoundingBox();
        BoundingBox imgBbox = new BoundingBox(ImageHandler.wrap(img));
        int[] box = {imgBbox.xmin, imgBbox.xmax, imgBbox.ymin, imgBbox.ymax, imgBbox.zmin, imgBbox.zmax};
        if (bbox.xmin < 0 || bbox.xmax > imgBbox.xmax || bbox.ymin < 0 || bbox.ymax > imgBbox.ymax
                || bbox.zmin < 0 || bbox.zmax > imgBbox.zmax) {
            Object3DInt objDilImg = new Object3DInt();
            for (Object3DPlane p : objDil.getObject3DPlanes()) {
                for (VoxelInt v : p.getVoxels()) {
                    if (v.isInsideBoundingBox(box))
                        objDilImg.addVoxel(v);
                }
            }
            return(objDilImg);
        }
        else
            return(objDil);
    }
    
   
    
    public void InitResults(BufferedWriter results) throws IOException {
        // write results headers
        results.write("Image Name\tImage background (median of min intensity stack)\tVessel volume (µm3)\tGeneX in vessel\tGeneX Volume in vessel (µm3)\t"
                + "GeneX Intensity sum in vessel\tNormalized GeneX Intensity sum in vessel\tGeneX out vessel\tGeneX Volume out vessel (µm3)\tGeneX Intensity sum out vessel"
                + "\tNornalized GeneX out vessel\n");
        results.flush();
    }
    
  
    /**
     * save images objects population
     * @param imgGeneX
     * @param vesselsPop
     * @param genesXIn
     * @param genesXOut
     * @param outDirResults
     * @param rootName
     */
    public void saveCellsLabelledImage (ImagePlus imgGeneX, Objects3DIntPopulation vesselsPop, Objects3DIntPopulation genesXIn, Objects3DIntPopulation genesXOut,
            String outDirResults, String rootName) {
        // red geneIn , green geneOut, blue vessels
        ImageHandler imgVessels = ImageHandler.wrap(imgGeneX).createSameDimensions();
        ImageHandler imgGenesIn = ImageHandler.wrap(imgGeneX).createSameDimensions();
        ImageHandler imgGenesOut = ImageHandler.wrap(imgGeneX).createSameDimensions();
        // draw vessels population
        for (Object3DInt ob : vesselsPop.getObjects3DInt()) 
                ob.drawObject(imgVessels, 255);
        // draw genes In / Out population
        for (Object3DInt ob : genesXIn.getObjects3DInt()) 
                ob.drawObject(imgVessels, 255);
        for (Object3DInt ob : genesXOut.getObjects3DInt()) 
                ob.drawObject(imgVessels, 255);
        ImagePlus[] imgColors = {imgGenesIn.getImagePlus(), imgGenesOut.getImagePlus(), imgVessels.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_Objects.tif");
        imgVessels.closeImagePlus();
        imgGenesIn.closeImagePlus();
        imgGenesOut.closeImagePlus();
    }
    
    
    
    /**
     * Apply StarDist 2D slice by slice
     * Label detections in 3D
     * @return objects population
     */
    public Objects3DIntPopulation stardistPop(ImagePlus imgGene, ArrayList<Roi> rois) throws IOException{
        ImagePlus img = new Duplicator().run(imgGene);
        // StarDist
        File starDistModelFile = new File(stardistModelsPath+File.separator+stardistFociModel);
        StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
        star.loadInput(img);
        star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThresh, stardistOverlayThresh, stardistOutput);
        star.run();
        closeImages(img);

        // Label detections in 3D
        ImagePlus imgOut = star.getLabelImagePlus();       
        ImagePlus imgLabels = star.associateLabels(imgOut);
        imgLabels.setCalibration(cal); 
        closeImages(imgOut);
        Objects3DIntPopulation pop = new Objects3DIntPopulation();
        if (rois != null) {
            ClearCLBuffer imgCl = clij2.push(imgLabels);
            ClearCLBuffer fillImg = fillImg(imgCl, rois);
            clij2.release(imgCl);
            ImagePlus imgFill = clij2.pull(fillImg);
            clij2.release(fillImg);
            imgFill.setCalibration(cal);
            pop = new Objects3DIntPopulation(ImageHandler.wrap(imgFill));
            closeImages(imgFill);
        }
        else 
            pop = new Objects3DIntPopulation(ImageHandler.wrap(imgLabels));
       closeImages(imgLabels);
       return(pop);
   }
    
    
    /**
     * Find gene associated or not to vessel
     * 
     * @param geneXDots
     * @param vesselsPop
     * @param imgGeneX
     * @param in
     * @return 
     */
   public Objects3DIntPopulation findGeneIn_OutVessel(Objects3DIntPopulation geneXDots, Objects3DIntPopulation vesselsPop, ImagePlus imgGeneX, boolean in) {
        Objects3DIntPopulation pop = new Objects3DIntPopulation();
        ImageHandler imh = ImageHandler.wrap(imgGeneX).createSameDimensions();
        geneXDots.drawInImage(imh);
        ImageHandler imhCopy = imh.duplicate();
        for (Object3DInt obj : vesselsPop.getObjects3DInt()) {
            Object3DInt dilateObj = dilateObj(imgGeneX, obj, dilVessel);
            dilateObj.drawObject(imh, 0);
        }
       
        if (!in)
            pop = new Objects3DIntPopulation(imh);
        else {
            ImageCalculator imgCal = new ImageCalculator();
            ImagePlus imgSub = imgCal.run("subtract stack create", imhCopy.getImagePlus(), imh.getImagePlus());
            ImageHandler imhSub = ImageHandler.wrap(imgSub);
            pop = new Objects3DIntPopulation(imhSub);
        }
        imh.closeImagePlus();
        imhCopy.closeImagePlus();
        return(pop);  
   }
   
   public void writeResults(Objects3DIntPopulation vesselsPop, Objects3DIntPopulation geneInVessel, Objects3DIntPopulation geneOutVessel, 
           ImagePlus imgGeneX, String imgName, BufferedWriter results) throws IOException {
       double bgGene = findBackground(imgGeneX);
       double vesselVol = findPopVolume(vesselsPop);
       int genesInVessel = geneInVessel.getNbObjects();
       double geneInVol = findPopVolume(geneInVessel);
       double geneInInt = findPopIntensity(geneInVessel, imgGeneX);
       double geneInIntBgCor = geneInInt - bgGene/pixVol;
       int genesOutVessel = geneOutVessel.getNbObjects();
       double geneOutVol = findPopVolume(geneOutVessel);
       double geneOutInt = findPopIntensity(geneOutVessel, imgGeneX);
       double geneOutIntBgCor = geneOutInt - bgGene/pixVol;
       results.write(imgName+"\t"+bgGene+"\t"+vesselVol+"\t"+genesInVessel+"\t"+geneInVol+"\t"+geneInInt+"\t"+geneInIntBgCor+"\t"+genesOutVessel+"\t"+geneOutVol+"\t"+geneOutInt+
               "\t"+geneOutIntBgCor+"\n");
       results.flush();
   }
}
