package contMensili;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.MyPlot;
import utils.ReadDicom;
import utils.UtilAyv;

public class p16rmn_Test {

	@Before
	public void setUp() throws Exception {
		new ImageJ(ImageJ.NORMAL);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testVersion() {
		assertFalse(IJ.versionLessThan("1.42p"));
	}

	@Test
	public final void testAnalProfFirstSlab() {
		double vetProfile[] = new double[4];
		double lato = 0;
		double[] vetRefPosition = { 54.0, 53.0, 56.0, 208.0, }; // [xStart,yStart,xEnd,yEnd]
		
		
		String path1 = "./Test2/066";
		ImagePlus imp3 = UtilAyv.openImageMaximized(path1);
		Overlay over3 = new Overlay();
		imp3.setOverlay(over3);

		double dimPixel = 0.78125;
		int ra1 = 13;
		
		
		Line lin1 = new Line((int) vetRefPosition[0], (int) vetRefPosition[1], (int) vetRefPosition[2],
				(int) vetRefPosition[3]);
		
		imp3.setRoi(lin1);
		imp3.updateAndRepaintWindow();

		Roi roi = imp3.getRoi();
		Line line2 = (Line) roi;
		lato = line2.getRawLength();
		double kappa = 156 / lato;
		ra1 = (int) (13 / kappa);
		vetProfile[0] = 5 / kappa;
		vetProfile[1] = 26 / kappa;
		vetProfile[2] = 150 / kappa;
		vetProfile[3] = 26 / kappa;

		
		boolean slab = true;
		boolean invert = false;
		// N.B: mettendo verbose a true si puo' vedere la grafica funzionare
		boolean verbose = false;
		boolean bLabelSx = false;
		double[] dsd1 = new p16rmn_().analProf(imp3, vetRefPosition,
				vetProfile, ra1, slab, invert, verbose, bLabelSx, dimPixel, over3);
		
		double fwhm = dsd1[0];
		double peak = dsd1[1];
		assertEquals(8.090445406547264, fwhm, 1e-25);
		assertEquals(44.53125, peak, 1e-25);
	}

	
	@Test
	public final void testAnalSecondSlab() {
		double vetProfile[] = new double[4];
		double lato = 0;
		double[] vetRefPosition = { 54.0, 53.0, 56.0, 208.0, }; // [xStart,yStart,xEnd,yEnd]
		
		
		String path1 = "./Test2/066";
		ImagePlus imp3 = UtilAyv.openImageMaximized(path1);
		Overlay over3 = new Overlay();
		imp3.setOverlay(over3);

		double dimPixel = 0.78125;
		int ra1 = 13;
		
		
		Line lin1 = new Line((int) vetRefPosition[0], (int) vetRefPosition[1], (int) vetRefPosition[2],
				(int) vetRefPosition[3]);
		
		imp3.setRoi(lin1);
		imp3.updateAndRepaintWindow();

		Roi roi = imp3.getRoi();
		Line line2 = (Line) roi;
		lato = line2.getRawLength();
		double kappa = 156 / lato;
		ra1 = (int) (13 / kappa);
		vetProfile[0] = 5 / kappa;
		vetProfile[1] = 60 / kappa;
		vetProfile[2] = 150 / kappa;
		vetProfile[3] = 60 / kappa;

		
		boolean slab = true;
		boolean invert = false;
		// N.B: mettendo verbose a true si puo' vedere la grafica funzionare
		boolean verbose = false;
		boolean bLabelSx = false;
		double[] dsd1 = new p16rmn_().analProf(imp3, vetRefPosition,
				vetProfile, ra1, slab, invert, verbose, bLabelSx, dimPixel, over3);
		
		double fwhm = dsd1[0];
		double peak = dsd1[1];
		assertEquals(8.090445406547264, fwhm, 1e-25);
		assertEquals(44.53125, peak, 1e-25);
	}

	
	
	
	@Test
	public final void testAnalProfFirstWedge() {
		double vetProfile[] = new double[4];
		double lato = 0;
		double[] vetRefPosition = { 54.0, 53.0, 56.0, 208.0, }; // [xStart,yStart,xEnd,yEnd]
		String path1 = "./Test2/066";
		ImagePlus imp3 = UtilAyv.openImageMaximized(path1);
		Overlay over3 = new Overlay();
		imp3.setOverlay(over3);
		double dimPixel = 0.78125;
		int ra1 = 13;	
		Line lin1 = new Line((int) vetRefPosition[0], (int) vetRefPosition[1], (int) vetRefPosition[2],
				(int) vetRefPosition[3]);
		imp3.setRoi(lin1);
		imp3.updateAndRepaintWindow();
		Roi roi = imp3.getRoi();
		Line line2 = (Line) roi;
		lato = line2.getRawLength();
		double kappa = 156 / lato;
		ra1 = (int) (13 / kappa);
		vetProfile[0] = 5 / kappa;
		vetProfile[1] = 96 / kappa;
		vetProfile[2] = 150 / kappa;
		vetProfile[3] = 96 / kappa;
		boolean slab = false;
		boolean invert = true;
		boolean verbose = false;
		boolean bLabelSx = false;
		double[] dsd1 = new p16rmn_().analProf(imp3, vetRefPosition,
				vetProfile, ra1, slab, invert, verbose, bLabelSx, dimPixel, over3);	
		double fwhm = dsd1[0];
		double peak = dsd1[1];
		assertEquals(8.090445406547264, fwhm, 1e-25);
		assertEquals(44.53125, peak, 1e-25);
	}
	
	@Test
	public final void testAnalProfSecondWedge() {
		double vetProfile[] = new double[4];
		double lato = 0;
		double[] vetRefPosition = { 54.0, 53.0, 56.0, 208.0, }; // [xStart,yStart,xEnd,yEnd]
		String path1 = "./Test2/066";
		ImagePlus imp3 = UtilAyv.openImageMaximized(path1);
		Overlay over3 = new Overlay();
		imp3.setOverlay(over3);
		double dimPixel = 0.78125;
		int ra1 = 13;	
		Line lin1 = new Line((int) vetRefPosition[0], (int) vetRefPosition[1], (int) vetRefPosition[2],
				(int) vetRefPosition[3]);
		imp3.setRoi(lin1);
		imp3.updateAndRepaintWindow();
		Roi roi = imp3.getRoi();
		Line line2 = (Line) roi;
		lato = line2.getRawLength();
		double kappa = 156 / lato;
		ra1 = (int) (13 / kappa);
		vetProfile[0] = 5 / kappa;
		vetProfile[1] = 130 / kappa;
		vetProfile[2] = 150 / kappa;
		vetProfile[3] = 130 / kappa;
		boolean slab = false;
		boolean invert = false;
		boolean verbose = true;
		boolean bLabelSx = false;
		double[] dsd1 = new p16rmn_().analProf(imp3, vetRefPosition,
				vetProfile, ra1, slab, invert, verbose, bLabelSx, dimPixel, over3);	
		double fwhm = dsd1[0];
		double peak = dsd1[1];
		assertEquals(8.090445406547264, fwhm, 1e-25);
		assertEquals(44.53125, peak, 1e-25);
	}

}