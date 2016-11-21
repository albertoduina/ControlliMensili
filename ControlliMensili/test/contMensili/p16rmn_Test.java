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
	public final void testBubbleSortPath() {

		String[] path = InputOutput.readStringArrayFromFile("./data/vet03.txt");
		String[] position = { "6", "-14", "-10", "-6", "-2", "2", "10", "14",
				"-12", "-8", "-4", "0", "4", "8", "12" };
		String[] pathSortatoCampione = InputOutput
				.readStringArrayFromFile("./data/vet04.txt");
		String[] pathSortato = new p6rmn_().bubbleSortPath(path, position);
		assertTrue(UtilAyv.compareVectors(pathSortatoCampione, pathSortato,
				"testBubbleSortPath"));
	}

	@Test
	public final void testBubbleSortPathUno() {
		String[] path = { ".\\test2\\BTMA_09testP6" };
		String[] position = { "6" };
		String[] pathSortatoCampione = { ".\\test2\\BTMA_09testP6" };
		String[] pathSortato = new p6rmn_().bubbleSortPath(path, position);
		assertTrue(UtilAyv.compareVectors(pathSortatoCampione, pathSortato, ""));
	}

	@Test
	public final void testBubbleSortPathNull() {

		String[] path = InputOutput.readStringArrayFromFile("./data/vet03.txt");
		String[] position = null;
		String[] pathSortato = new p6rmn_().bubbleSortPath(path, position);
		assertNull(pathSortato);
	}

	@Test
	public final void testGetLinePixelsHorizontal() {
		/*
		 * I risultati ricavati da p6rmn_ sono cambiati con l'uscita della
		 * release 1.42p di ImageJ. Tale cambiamento � dovuto alle modifiche
		 * apportate a ij\plugin\Straightener.java, in pratica le variabili che
		 * definivano le coordinate di inizio e fine della linea sono passate da
		 * float a double. Ci� ha provocato una variazione sui valori dei pixel
		 * delle linee inclinate con spessore > 1. Con questo test vengono
		 * verificati i valori forniti da IJ dalla versione 1.42p e successive
		 */
		String[] list = { "BT2A_testP6" };
		String[] path1 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path = path1[0];
		// String path = "./test2/BT2A_testP6";
		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path, true);
		int c2x = 63;
		int c2y = 67;
		int d2x = 195;
		int d2y = 67;
		Line.setWidth(11);
		double[] profiM1 = new p6rmn_().getLinePixels(imp1, c2x, c2y, d2x, d2y);
		double[] expectedOutput = InputOutput
				.readDoubleArrayFromFile("./data/vet05.txt");
		Line.setWidth(1);

		MyLog.logVector(profiM1, "profiM1");
		MyLog.logVector(expectedOutput, "expectedOutput");

		assertTrue(UtilAyv.compareVectors(expectedOutput, profiM1, 1e-8, ""));
	}

	@Test
	public final void testGetLinePixels() {
		String[] list = { "BT2A_testP6" };
		String[] path1 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path = path1[0];
		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path, true);
		int c2x = 63;
		int c2y = 67;
		int d2x = 195;
		int d2y = 66;
		Line.setWidth(11);

		double[] profiM1 = new p6rmn_().getLinePixels(imp1, c2x, c2y, d2x, d2y);
		Line.setWidth(1);
		//
		// utils.logVector(profiM1);
		//
		double[] expectedOutput = InputOutput
				.readDoubleArrayFromFile("./data/vet06.txt");
		assertTrue(UtilAyv.compareVectors(expectedOutput, profiM1, 1e-8, ""));
	}

	@Test
	public final void testAnalProfNull() {
		double vetProfile[] = { 12.00024654579077, 31.200641019056,
				144.00295854948925, 31.200641019056 }; // [xStart,yStart,xEnd,yEnd]
		double lato = 0;
		double[] vetTestPositions = { 51.0, 36.0, 52.0, 192.0, }; // [xStart,yStart,xEnd,yEnd]
		String[] list = { "BT2A_testP6" };
		String[] path2 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path2[0];
		ImagePlus imp3 = UtilAyv.openImageMaximized(path1);
		double dimPixel = 0.78125;
		int ra1 = 13;
		imp3.setRoi(new Line((int) vetTestPositions[0],
				(int) vetTestPositions[1], (int) vetTestPositions[2],
				(int) vetTestPositions[3], imp3));
		imp3.updateAndRepaintWindow();
		Roi r1 = imp3.getRoi();
		assert (r1 != null);
		Roi roi = imp3.getRoi();
		Line line2 = (Line) roi;
		lato = line2.getRawLength();
		assertEquals(156.00320509528, lato, 1e-25);
		boolean slab = true;
		boolean invert = false;
		boolean verbose = false;
		boolean bLabelSx = false;
		imp3 = null;
		double[] dsd1 = new p6rmn_().analProf(imp3, vetTestPositions,
				vetProfile, ra1, slab, invert, verbose, bLabelSx, dimPixel);
		assertNull(dsd1);
	}

	@Test
	public final void testAnalProfFirstSlab() {
		double vetProfile[] = { 12.00024654579077, 31.200641019056,
				144.00295854948925, 31.200641019056 }; // [xStart,yStart,xEnd,yEnd]
		double lato = 0;
		double[] vetTestPositions = { 51.0, 36.0, 52.0, 192.0, }; // [xStart,yStart,xEnd,yEnd]
		String[] list = { "BT2A_testP6" };
		String[] path2 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path2[0];
		ImagePlus imp3 = UtilAyv.openImageMaximized(path1);
		double dimPixel = 0.78125;
		int ra1 = 13;
		imp3.setRoi(new Line((int) vetTestPositions[0],
				(int) vetTestPositions[1], (int) vetTestPositions[2],
				(int) vetTestPositions[3], imp3));
		imp3.updateAndRepaintWindow();
		Roi r1 = imp3.getRoi();
		assert (r1 != null);
		Roi roi = imp3.getRoi();
		Line line2 = (Line) roi;
		lato = line2.getRawLength();
		assertEquals(156.00320509528, lato, 1e-25);
		boolean slab = true;
		boolean invert = false;
		// N.B: mettendo verbose a true si pu� vedere la grafica funzionare
		boolean verbose = false;
		boolean bLabelSx = false;
		double[] dsd1 = new p6rmn_().analProf(imp3, vetTestPositions,
				vetProfile, ra1, slab, invert, verbose, bLabelSx, dimPixel);
		double fwhm = dsd1[0];
		double peak = dsd1[1];
		assertEquals(10.090520971808136, fwhm, 1e-25);
		assertEquals(42.96875, peak, 1e-25);
	}

	@Test
	public final void testAnalProfFirstWedge() {
		double vetProfile[] = { 12.00024654579077, 93.60192305716801,
				144.00295854948925, 93.60192305716801 }; // [xStart,yStart,xEnd,yEnd]
		double lato = 0;
		double[] vetTestPositions = { 51.0, 36.0, 52.0, 192.0 }; // [xStart,yStart,xEnd,yEnd]
		String[] list = { "BT2A_testP6" };
		String[] path2 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path2[0];
		ImagePlus imp3 = UtilAyv.openImageMaximized(path1);
		double dimPixel = 0.78125;
		int ra1 = 13;
		imp3.setRoi(new Line((int) vetTestPositions[0],
				(int) vetTestPositions[1], (int) vetTestPositions[2],
				(int) vetTestPositions[3], imp3));
		imp3.updateAndRepaintWindow();
		Roi r1 = imp3.getRoi();
		assert (r1 != null);
		Roi roi = imp3.getRoi();
		Line line2 = (Line) roi;
		lato = line2.getRawLength();
		assertEquals(156.00320509528, lato, 1e-25);
		boolean slab = false;
		boolean invert = true;
		// N.B: mettendo verbose a true si pu� vedere la grafica funzionare
		boolean verbose = false;
		boolean bLabelSx = false;
		double[] dsd1 = new p6rmn_().analProf(imp3, vetTestPositions,
				vetProfile, ra1, slab, invert, verbose, bLabelSx, dimPixel);
		double fwhm = dsd1[0];
		double peak = dsd1[1];
		assertEquals(8.090445406547264, fwhm, 1e-25);
		assertEquals(44.53125, peak, 1e-25);
	}

	@Test
	public final void testCalcFwhm() {
		// test con input numerico, non dipendente da ImageJ
		int[] isd3 = { 50, 49, 62, 63 };
		double[] profile1 = InputOutput
				.readDoubleArrayFromFile("./data/vet07.txt");
		boolean slab = true;
		double dimPixel = 0.78125;
		double[] outFwhm = new p6rmn_()
				.calcFwhm(isd3, profile1, slab, dimPixel);
		assertEquals(10.09261130074483, outFwhm[0], 1e-25);
		assertEquals(42.96875, outFwhm[1], 1e-25);
	}

	@Test
	public final void testBaselineCorrection() {
		// test con input numerico, non dipendente da ImageJ

		double[] profiM1 = InputOutput
				.readDoubleArrayFromFile("./data/vet08.txt");
		double media1 = 2240.4160583941607;
		double media2 = 2313.6204379562046;
		double[] profiB1 = new p6rmn_().baselineCorrection(profiM1, media1,
				media2);
		double[] expectedOutput = InputOutput
				.readDoubleArrayFromFile("./data/vet09.txt");
		assertTrue(UtilAyv.compareVectors(expectedOutput, profiB1, 1e-8, ""));
	}

	@Test
	public final void testCreateErf() {

		double[] profiB1 = InputOutput
				.readDoubleArrayFromFile("./data/vet10.txt");
		Plot p2 = MyPlot.basePlot(profiB1, "title", Color.red);
		p2.show();

		// test con input numerico, non dipendente da ImageJ
		boolean invert = true;
		double[] profiE1 = new p6rmn_().createErf(profiB1, invert);
		Plot p1 = MyPlot.basePlot(profiE1, "title", Color.red);
		p1.show();
		// MyLog.waitHere();
		double[] expectedOutput = InputOutput
				.readDoubleArrayFromFile("./data/vet21_p6.txt");
		// MyLog.logVector(profiE1, "profiE1");
		// MyLog.logVector(expectedOutput, "expectedOutput");
		// MyLog.waitHere();

		assertTrue(UtilAyv.compareVectors(expectedOutput, profiE1, 1e-8, ""));
	}

	@Test
	public final void testCreateErfReversed() {

		double[] profiA1 = InputOutput
				.readDoubleArrayFromFile("./data/vet10.txt");
		double[] profiB1 = UtilAyv.reverseVector(profiA1);

		Plot p2 = MyPlot.basePlot(profiB1, "title", Color.red);
		p2.show();

		// test con input numerico, non dipendente da ImageJ
		boolean invert = true;
		double[] profiE1 = new p6rmn_().createErf(profiB1, invert);
		Plot p1 = MyPlot.basePlot(profiE1, "title", Color.red);
		p1.show();

		double[] expectedOutput = InputOutput
				.readDoubleArrayFromFile("./data/vet22_p6.txt");

		// MyLog.logVector(expectedOutput, "expectedOutput");
		// MyLog.waitHere();
		// MyLog.logVector(profiE1, "profiE1");
		// MyLog.waitHere();

		assertTrue(UtilAyv.compareVectors(expectedOutput, profiE1, 1e-8, ""));
	}

	@Test
	public final void testAnalPlot1() {
		// test con input numerico, non dipendente da ImageJ
		boolean bSlab = true;
		double[] profile1 = InputOutput
				.readDoubleArrayFromFile("./data/vet12.txt");
		int isd3[] = new p6rmn_().analPlot1(profile1, bSlab);
		assertEquals(50, isd3[0]);
		assertEquals(49, isd3[1]);
		assertEquals(62, isd3[2]);
		assertEquals(63, isd3[3]);
	}

	@Test
	public final void testSpessStrato() {
		// test numerico
		// UtilAyv utils = new UtilAyv();
		p6rmn_ p6 = new p6rmn_();
		double R1 = 10.09261130074483;
		double R2 = 13.018490123004645;
		double sTeor = 2.0;
		double dimPix2 = 0.78125;
		double[] result = p6.spessStrato(R1, R2, sTeor, dimPix2);
		assertEquals(2.2114617397133083, result[0], 1e-25);
		assertEquals(2.211461739713308, result[1], 1e-25);
		assertEquals(10.830131267778127, result[2], 1e-25);
		assertEquals(10.573086985665414, result[3], 1e-25);
	}

	@Test
	public final void testInterdictionGood() {
		double xStart = 20;
		double yStart = 20;
		double xEnd = 52;
		double yEnd = 70;
		double[] vetline = new double[4]; // [ xStart, yStart, xEnd, yEnd ]
		vetline[0] = xStart;
		vetline[1] = yStart;
		vetline[2] = xEnd;
		vetline[3] = yEnd;
		int matrix = 512;
		boolean flag = new p6rmn_().interdiction(vetline, matrix);
		assertFalse(flag);
	}

	@Test
	public final void testInterdictionInterference() {
		double xStart = 2;
		double yStart = 20;
		double xEnd = 52;
		double yEnd = 70;
		double[] vetline = new double[4]; // [ xStart, yStart, xEnd, yEnd ]
		vetline[0] = xStart;
		vetline[1] = yStart;
		vetline[2] = xEnd;
		vetline[3] = yEnd;
		int matrix = 512;
		boolean flag = new p6rmn_().interdiction(vetline, matrix);
		assertTrue(flag);
	}

	@Test
	public final void testStackBuilderNull() {
		String[] path = null;

		ImagePlus newStack = new p6rmn_().stackBuilder(path, false);

		assertNull(newStack);
	}

	@Test
	public final void testStackBuilderZero() {
		String[] path = {};
		ImagePlus newStack = new p6rmn_().stackBuilder(path, false);
		assertNull(newStack);
	}

	@Test
	public final void testStackBuilderUno() {
		String[] list = { "BT2A_testP6" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		ImagePlus newStack = new p6rmn_().stackBuilder(path, true);
		assertTrue(newStack.getNSlices() == 1);
	}

	@Test
	public final void testStackBuilderDue() {
		String[] list = { "BT2A_testP6", "HT5A2_testP6" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		ImagePlus newStack = new p6rmn_().stackBuilder(path, true);
		assertTrue(newStack.getNSlices() == 2);
	}

	@Test
	public final void testStackBuilderTre() {
		String[] list = { "BT2A_testP6", "HT5A2_testP6", "BT2A_testP6" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		// MyLog.logVector(path, "path");

		// qui interrogo lo stack senza selezionare la fetta
		ImagePlus newStack = new p6rmn_().stackBuilder(path, true);
		assertTrue(newStack.getNSlices() == 3);
		int row1 = ReadDicom.readInt(ReadDicom.readDicomParameter(newStack,
				MyConst.DICOM_ROWS));
		assertEquals(256, row1);
		// sperimento ora la tecnica di estrazione dei dati per una ben precisa
		// fetta
		ImageStack stack2 = newStack.getImageStack();
		ImageProcessor ip3 = stack2.getProcessor(1);
		String titolo = stack2.getShortSliceLabel(1);
		String sliceInfo1 = stack2.getSliceLabel(1);
		ImagePlus imp3 = new ImagePlus(titolo, ip3);
		imp3.setProperty("Info", sliceInfo1);
		String pos2 = ReadDicom.readDicomParameter(imp3,
				MyConst.DICOM_IMAGE_POSITION);
		String truePosition = ReadDicom.readSubstring(pos2, 3);
		assertEquals("1.71565", truePosition);
	}

	@Test
	public final void testStackBuilderQuattro() {
		String[] list = { "BT2A_testP6", "HT5A2_testP6", "BT2A_testP6" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		// qui interrogo lo stack senza selezionare la fetta
		String DICOM_ROWS = "0028,0010";
		// String DICOM_IMAGE_POSITION = "0020,0032";
		ImagePlus newStack = new p6rmn_().stackBuilder(path, true);

		assertTrue(newStack.getNSlices() == 3);
		newStack.setSliceWithoutUpdate(1);
		int row1 = ReadDicom.readInt(ReadDicom.readDicomParameter(newStack,
				DICOM_ROWS));
		// System.out.printf("String= " + row1);
		assertEquals(256, row1);
	}

	@Test
	public final void testCreatePlot2() {
		double[] profile1 = InputOutput
				.readDoubleArrayFromFile("./data/vet12.txt");
		boolean bslab = true;
		boolean bLabelSx = true;
		String sTitolo = "createPlotWork";
		boolean bFw = true;
		new p6rmn_().createPlot2(profile1, bslab, bLabelSx, sTitolo, bFw);
		// ButtonMessages.ModelessMsg("", "CONTINUA");
		IJ.wait(500);
	}

	@Test
	public final void testTestSiemens() {
		// Siemens
		String[] list = { "BT2A_testP6" };
		String[] path1 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		// String[] path1 = { "./test2/BT2A_testP6" };
		double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_SIEMENS;
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;

		ResultsTable rt = new p6rmn_().mainThickness(path1, autoArgs,
				vetRefPosition, autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt);
		boolean ok = UtilAyv.verifyResults1(vetResults,
				new p6rmn_().referenceSiemens(), MyConst.P6_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testTestGe() {
		String[] list = { "HT5A2_testP6" };
		String[] path1 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		// String[] path1 = { "./test2/HT5A2_testP6" };
		double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_GE;
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;

		ResultsTable rt = new p6rmn_().mainThickness(path1, autoArgs,
				vetRefPosition, autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt);
		boolean ok = UtilAyv.verifyResults1(vetResults,
				new p6rmn_().referenceGe(), MyConst.P6_vetName);
		assertTrue(ok);
	}

	@Test
	public final void testTestSilent() {
		new p6rmn_().selfTestSilent();
	}

	@Test
	public void testMainThickness() {
		String[] list = { "BT2A_testP6" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		// String[] path = { "./Test2/BT2A_testP6" };
		String autoArgs = "0";
		double[] vetRefPosition = MyConst.P6_REFERENCE_LINE_SIEMENS;
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;

		ResultsTable rt = new p6rmn_().mainThickness(path, autoArgs,
				vetRefPosition, autoCalled, step, verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt);
		boolean ok = UtilAyv.verifyResults1(vetResults,
				new p6rmn_().referenceSiemens(), MyConst.P6_vetName);
		assertTrue(ok);
	}

}