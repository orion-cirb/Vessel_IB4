package Vessel_IB4;

import Vessel_IB4_Utils.Vessel_Processing;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.BF;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import loci.plugins.in.ImporterOptions;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import java.util.Collections;
import mcib3d.geom2.Objects3DIntPopulation;


/**
 * @author ORION-CIRB
 */
public class Vessel_IB4 implements PlugIn {
    
    private Vessel_IB4_Utils.Vessel_Processing process = new Vessel_Processing();
    
    public void run(String arg) {
        try {
            if (!process.checkInstalledModules() || (!process.checkStarDistModels())) {
                return;
            }

            String imageDir = IJ.getDirectory("Choose directory containing images...");
            if (imageDir == null) {
                return;
            }

            // Find images with extension
            String file_ext = process.findImageType(new File(imageDir));
            ArrayList<String> imageFiles = process.findImages(imageDir, file_ext);
            if (imageFiles.isEmpty()) {
                IJ.showMessage("Error", "No images found with " + file_ext + " extension");
                return;
            }

            // Create output folder
            String outDirResults = imageDir + File.separator+ "Results"+ File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }

            // Initialize results file
            FileWriter  fwResults = new FileWriter(outDirResults +"results.xls",false);
            BufferedWriter results = new BufferedWriter(fwResults);
            process.initResults(results);

            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));

            // Find image calibration
            process.findImageCalib(meta);

            // Find channels name
            String[] channels = process.findChannels(imageFiles.get(0), meta, reader);

            // Generate dialog box
            String[] chs = process.dialog(channels);
            if (chs == null) {
                IJ.showMessage("Error", "Plugin canceled");
                return;
            }

            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                process.print("--- ANALYZING IMAGE " + rootName + " ------");

                reader.setId(f);
                reader.setSeries(0);
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);

                // Check if ROI file exists: used later to clear regions containing artifacts
                ArrayList<Roi> rois = new ArrayList<>();
                String roiFile = new File(imageDir+rootName+".zip").exists() ? imageDir+rootName+".zip" : imageDir+rootName+".roi";
                if (new File(roiFile).exists()) {
                    System.out.println("ROI file found for image " + rootName);
                    RoiManager rm = new RoiManager(false);
                    if (rm != null)
                        rm.reset();
                    else
                        rm = new RoiManager(false);
                    rm.runCommand("Open", roiFile);
                    Collections.addAll(rois, rm.getRoisAsArray());
                } else {
                    System.out.println("No ROI file found for image " + rootName);
                }

                // Open vessels channel
                process.print("Opening vessels channel...");
                int indexCh = ArrayUtils.indexOf(channels, chs[0]);
                ImagePlus imgVessel = BF.openImagePlus(options)[indexCh];

                // Find vessels
                Objects3DIntPopulation vesselsPop = process.vesselsDetection(imgVessel, rois);
                Objects3DIntPopulation dilatedVesselsPop = process.popDilation(vesselsPop, imgVessel);
                process.closeImage(imgVessel);

                // Open RNA channel
                process.print("Opening RNA channel...");
                indexCh = ArrayUtils.indexOf(channels, chs[1]);
                ImagePlus imgRNA = BF.openImagePlus(options)[indexCh];

                // Find RNA dots
                Objects3DIntPopulation rnaPop = process.stardistDetection(imgRNA, rois);
                // Find RNA dots in vessels
                Objects3DIntPopulation rnaInVessels = process.findRNAInOutVessels(rnaPop, dilatedVesselsPop, imgRNA, true);
                // Find RNA dots out of vessels
                Objects3DIntPopulation rnaOutVessels = process.findRNAInOutVessels(rnaPop, dilatedVesselsPop, imgRNA, false);

                // Draw results in images
                process.drawResults(imgRNA, vesselsPop, rnaInVessels, rnaOutVessels, outDirResults, rootName);

                // Write results
                process.writeResults(vesselsPop, rnaInVessels, rnaOutVessels, imgRNA, rois, rootName, results);
                process.closeImage(imgRNA);
            }

            results.close();
            process.print("--- All done! ---");

        } catch (IOException | DependencyException | ServiceException | FormatException ex) {
                Logger.getLogger(Vessel_IB4.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
