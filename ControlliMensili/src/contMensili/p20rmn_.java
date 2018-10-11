package contMensili;

// set debug 290607

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.NewImage;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyAutoThreshold;
import utils.MyConst;
import utils.MyFileLogger;
import utils.MyLog;
import utils.MyMsg;
import utils.MyStackUtils;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.SimplexBasedRegressor;
import utils.TableCode;
import utils.TableSequence;
import utils.UtilAyv;
import utils.AboutBox;

/*
 * Copyright (C) 2007 Alberto Duina, SPEDALI CIVILI DI BRESCIA, Brescia ITALY
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/**
 * Calcola T1 e T2 utilizzando l'algoritmo simplex di Nelder & Mead.
 * 
 * Da MRI Analysis Calculator e da MRI Analysis Pack di Karl Schmidt i cui
 * sorgenti sono raggiungibili da http://www.quickvol.com/launch.html e su
 * sourceforge.net
 * 
 * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
 * directory plugins
 * 
 * Le formule di minimizzazione sono nella classe SimplexBasedRegressor
 * 
 * 29jan07 v 3.00 i risultati sono praticamente identici a MriCalc
 * 
 * @author Karl Schmidt - karl.schmidt@umassmed.edu
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 * 
 */
public class p20rmn_ implements PlugIn, Measurements {

	// ------------------------------------------------------------

	private static final int ABORT = 1;

	public static String VERSION = "Verifica T1 e T2";

	private static String TYPE = " >> CONTROLLO T1 / T2_________________";

	private static String fileDir = "";
	private static boolean debug = true;
	private static boolean mylogger = true;

	public static boolean debug1 = false;

	public static boolean DEBUG2 = false; // true attiva il debug

	private static boolean selftest = false; // non � bello, lo uso per colpa
												// del

	// maledetto filtro sul fondo

	public void run(String args) {

		UtilAyv.setMyPrecision();

		if (IJ.versionLessThan("1.43k"))
			return;

		// ---------------------------------------------------------------------------
		// nota bene: le seguenti istruzioni devono essere all'inizio, in questo
		// modo il messaggio "manca il file" viene emesso, altrimenti si ha una
		// eccezione
		// ----------------------------------------------------------------------
		try {
			Class.forName("utils.IW2AYV");
		} catch (ClassNotFoundException e) {
			IJ.error("ATTENZIONE, manca il file iw2ayv_xxx.jar");
			return;
		}
		// ----------------------------------------------------------------------------
		String className = this.getClass().getName();

		VERSION = className + "_build_" + MyVersion.getVersion()
				+ "_iw2ayv_build_" + MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");
		if (mylogger)
			MyFileLogger.logger.info("p20rmn_>>> fileDir= " + fileDir);
		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "", false);
		} else {
			autoMenu(args);
		}
		return;
	}

	/**
	 * Manual menu, invoked directly from the ImageJ plugins/ContMensili/p20rmn_
	 * 
	 * @param preset
	 * @param testDirectory
	 * @return
	 */
	public static ImagePlus manualMenu(int preset, String testDirectory,
			boolean testA) {
		boolean retry = false;
		boolean step = false;
		ImagePlus imp111 = null;
		List<String> listPath = new ArrayList<String>();

		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return null;
			case 2:
				new AboutBox().about("Controllo T1 e  T2",
						MyVersion.CURRENT_VERSION);
				// ab.about("Controllo T1 e  T2", this.getClass());
				retry = true;
				break;

			case 3:
				selfTestMenu();
				retry = true;
				break;
			case 4:
				step = true;
			case 5:
				boolean autoCalled = false;
				boolean verbose = true;
				boolean test = false;
				boolean silent = false;
				String[] vetPath = manualImageSelection();

				boolean typeT2 = p20rmn_.msgT1T2();
				ImageStack stack100 = stackOpener1(vetPath, typeT2);
				ImagePlus imp100 = new ImagePlus("newStack", stack100);
				// MyLog.waitHere("imp100= " + imp100);
				UtilAyv.showImageMaximized(imp100);

				Roi[] vetRoi = manualRoiPreparation(imp100);
				// MyLog.waitHere("imp100= " + imp100);

				int[] bMap = mapPreparation(imp100, vetRoi);
				// MyLog.waitHere("imp100= " + imp100);
				double filtro = filterPreparation(imp100, vetRoi, step);

				imp111 = doCalculation(stack100, filtro, bMap, typeT2, silent);
				if (imp111 == null) {
					MyLog.waitHere("imp111 == null");
					return null;
				}

				// prepUnifor(path1, path2, "0", autoCalled, step, verbose,
				// test);
				if (!testA)
					UtilAyv.afterWork();
				retry = true;
				break;
			}
		} while (retry);
		new AboutBox().close();
		if (!testA)
			UtilAyv.afterWork();
		return imp111;
	}

	/**
	 * Selezione manuale delle immagini, se ne viene selezionata solo una
	 * vengono selezionate tutte le immagini della directory
	 * 
	 * @return
	 */
	public static String[] manualImageSelection() {
		boolean continua1 = true;
		List<String> arrayPath = new ArrayList<String>();
		String path = "";
		String directory = "";
		String[] vetOut = null;
		do {
			OpenDialog openDial = new OpenDialog("Selezione immagine", "");
			path = openDial.getPath();
			if (path == null) {
				continua1 = false;
			} else {
				directory = openDial.getDirectory();
				arrayPath.add(path);
			}
		} while (continua1);
		String[] vetPath = ArrayUtils.arrayListToArrayString(arrayPath);
		// se � stato selezionato solo un file suppongo che si vogliano
		// selezionare tutti i file della directory.
		File dir1 = new File(directory);
		File[] allFiles = null;
		if (vetPath.length == 1) {
			allFiles = dir1.listFiles();
			vetOut = new String[allFiles.length];
			for (int i1 = 0; i1 < allFiles.length; i1++) {
				vetOut[i1] = allFiles[i1].getPath();
			}
		} else
			vetOut = vetPath;
		return vetOut;

	}

	/**
	 * Auto menu invoked from Sequenze_
	 * 
	 * @param autoArgs
	 * @return
	 */
	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p20 riceve " + autoArgs);
		boolean fast = Prefs.get("prefer.fast", "false").equals("true") ? true
				: false;

		boolean bstep = false;
		ImagePlus imp111 = null;

		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p20rmn_");
			selfTestSilent();
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);

		String codice = TableSequence.getCode(iw2ayvTable, vetRiga[0]);
		String cod = codice.substring(0, 2).trim();
		boolean typeT2 = false;
		if (cod.equals("T2"))
			typeT2 = true;
		else
			typeT2 = false;

		String[] vetPath = new String[nTokens];
		for (int i1 = 0; i1 < nTokens; i1++) {
			vetPath[i1] = TableSequence.getPath(iw2ayvTable, vetRiga[i1]);
		}

		boolean retry = false;
		boolean step = false;
		do {
			// int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE);
			int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
					TableSequence.getCode(iw2ayvTable, vetRiga[0]),
					TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
					vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				return 0;
			case 2:
				// new AboutBox().about("Controllo Uniformit�",
				// this.getClass());
				new AboutBox().about("Controllo Uniformit�",
						MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				step = true;
			case 4:
				boolean verbose = true;
				boolean test = false;
				boolean autoCalled = true;
				boolean silent = false;

				ImageStack stack100 = stackOpener1(vetPath, typeT2);
				ImagePlus imp100 = new ImagePlus("newStack", stack100);
				UtilAyv.showImageMaximized(imp100);

				Roi[] vetRoi = manualRoiPreparation(imp100);

				int[] bMap = mapPreparation(imp100, vetRoi);

				double filtro = filterPreparation(imp100, vetRoi, bstep);

				// MyLog.waitHere("filtro= " + filtro);

				imp111 = doCalculation(stack100, filtro, bMap, typeT2, silent);
				if (imp111 == null)
					MyLog.waitHere("imp111 == null");

				boolean autocalled = false;
//				String[][] tabCodici = TableCode
//						.loadMultipleTable(MyConst.CODE_GROUP);
//
				
				TableCode tc1= new TableCode();
				String[][] tabCodici = tc1.loadMultipleTable( "codici", ".csv");

				// String[] info1 = ReportStandardInfo.getSimpleStandardInfo(
				// vetPath[0], imp100, tabCodici, VERSION, autocalled);

				String[] info1 = ReportStandardInfo.getSimpleStandardInfo(
						vetPath[0], imp100, tabCodici, VERSION, autocalled);

				ResultsTable rt1 = analyzeResultsImages(vetRoi, imp111, info1,
						typeT2, autoCalled, verbose, test, fast, silent);

				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt1);

				retry = false;
				// MyLog.waitHere("85, 29, 20, 20");
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;

	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {

		boolean verbose = true;
		boolean test = false;
		boolean autoCalled = true;
		boolean silent = true;
		boolean fast = true;
		ImagePlus imp111 = null;

		String[] list = { "T2MA_01testP2", "T2MA_02testP2", "T2MA_03testP2",
				"T2MA_04testP2", "T2MA_05testP2", "T2MA_06testP2",
				"T2MA_07testP2", "T2MA_08testP2", "T2MA_09testP2",
				"T2MA_10testP2", "T2MA_11testP2", "T2MA_12testP2",
				"T2MA_13testP2", "T2MA_14testP2", "T2MA_15testP2",
				"T2MA_16testP2", };
		String[] vetPath = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		boolean typeT2 = true;
		boolean bstep = false;
		ImageStack stack100 = stackOpener1(vetPath, typeT2);
		ImagePlus imp100 = new ImagePlus("newStack", stack100);

		String xpos = MyConst.P20_X_ROI_TESTSIEMENS;
		String ypos = MyConst.P20_Y_ROI_TESTSIEMENS;
		int diam = 20;

		Roi[] vetRoi = automaticRoiPreparation(imp100, xpos, ypos, diam);

		int[] bMap = mapPreparation(imp100, vetRoi);

		double filtro = filterPreparation(imp100, vetRoi, bstep);

		imp111 = doCalculation(stack100, filtro, bMap, typeT2, silent);
		if (imp111 == null)
			MyLog.waitHere("imp111 == null");

		
		TableCode tc1= new TableCode();
		String[][] tabCodici = tc1.loadMultipleTable( "codici", ".csv");

//		String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);

		boolean autocalled = false;
		// String[] info1 = ReportStandardInfo.getSimpleStandardInfo(vetPath[0],
		// imp100, tabCodici, VERSION, autocalled);

		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(vetPath[0],
				imp100, tabCodici, VERSION, autocalled);

		ResultsTable rt1 = analyzeResultsImages(vetRoi, imp111, info1, typeT2,
				autoCalled, verbose, test, fast, silent);

		double[] vetResults = UtilAyv.vectorizeResultsMultiple(rt1, 2);

		boolean ok = UtilAyv.verifyResults1(vetResults, referenceSiemens(),
				MyConst.P20_vetName);
		if (ok)
			IJ.log("Il test di p20rmn_ T2calculation � stato SUPERATO");
		else
			IJ.log("Il test di p20rmn_ T2calculation evidenzia degli ERRORI");

	}

	public static int[] mapPreparation(ImagePlus imp1, Roi[] vetRoi) {

		int width = imp1.getWidth();
		int height = imp1.getHeight();
		int[] bMap = new int[width * height];
		int offset;
		for (int y = 0; y < height; y++) {
			offset = y * width;
			for (int x = 0; x < width; x++) {
				bMap[x + offset] = 0;
				for (int i1 = 0; i1 < vetRoi.length - 1; i1++)
					if (vetRoi[i1].contains(x, y))
						bMap[x + offset] = 1;
			}
		}

		return bMap;
	}

	public static double filterPreparation(ImagePlus imp1, Roi[] vetRoi,
			boolean bstep) {

		Roi roi1 = vetRoi[vetRoi.length - 1];
		Rectangle rec1 = roi1.getBounds();

		int Rows = ReadDicom.readInt(ReadDicom.readDicomParameter(imp1,
				MyConst.DICOM_ROWS));
		int Columns = ReadDicom.readInt(ReadDicom.readDicomParameter(imp1,
				MyConst.DICOM_COLUMNS));

		int xpos = rec1.x;
		int ypos = rec1.y;
		int diam = rec1.width;

		xpos = Rows - rec1.width - 1;
		ypos = Columns - rec1.height - 1;

		boolean circular = true;
		boolean selftest = true;

		ImageStatistics statFondo = ImageUtils.backCalc(xpos, ypos, diam, imp1,
				bstep, circular, selftest);

		double mediaFondo = statFondo.mean;
		double dsFondo = statFondo.stdDev;
		double filtroFondo = mediaFondo * MyConst.P20_KMEDIA_FILTRO_FONDO
				+ dsFondo * MyConst.P20_KDEVST_FILTRO_FONDO;
		// MyLog.waitHere("xpos= " + xpos + " ypos= " + ypos + " diam= " + diam
		// + "mediaFondo= " + mediaFondo + " dsFondo= " + dsFondo
		// + " filtroFondo= " + filtroFondo);

		return filtroFondo;
	}

	/**
	 * Self test execution menu
	 */
	public static void selfTestMenu() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			boolean verbose = true;
			boolean test = false;
			boolean autoCalled = true;
			boolean silent = false;
			boolean fast = true;
			ImagePlus imp111 = null;

			switch (userSelection2) {
			case 1: {
				// GE

				String[] list = { "HT1A2_01testP2", "HT1A2_02testP2",
						"HT1A2_03testP2", "HT1A2_04testP2" };
				String[] vetPath = new InputOutput().findListTestImages2(
						MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

				boolean typeT2 = true;

				ImageStack stack2 = stackOpener1(vetPath, typeT2);
				ImagePlus imp2 = new ImagePlus("newStack", stack2);

				boolean step = false;

				String xpos = MyConst.P20_X_ROI_TESTGE;
				String ypos = MyConst.P20_Y_ROI_TESTGE;
				int diam = MyConst.P20_DIAM_ROI;

				Roi[] vetRoi = automaticRoiPreparation(imp2, xpos, ypos, diam);

				int[] bMap = mapPreparation(imp2, vetRoi);

				double filtro = filterPreparation(imp2, vetRoi, step);

				imp111 = doCalculation(stack2, filtro, bMap, typeT2, silent);
				if (imp111 == null)
					MyLog.waitHere("imp111 == null");

				String[] info1 = { "", "", "", "", "", "", "" };
				ResultsTable rt1 = analyzeResultsImages(vetRoi, imp111, info1,
						typeT2, autoCalled, verbose, test, fast, silent);

				rt1.show("Results");
				MyLog.waitHere();

				double[] vetResults = UtilAyv.vectorizeResultsMultiple(rt1, 2);

				boolean ok = UtilAyv.verifyResults1(vetResults,
						referenceSiemens(), MyConst.P20_vetName);

				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				break;
			}
			case 2:
				// Siemens
				String[] list = { "T2MA_01testP2", "T2MA_02testP2",
						"T2MA_03testP2", "T2MA_04testP2", "T2MA_05testP2",
						"T2MA_06testP2", "T2MA_07testP2", "T2MA_08testP2",
						"T2MA_09testP2", "T2MA_10testP2", "T2MA_11testP2",
						"T2MA_12testP2", "T2MA_13testP2", "T2MA_14testP2",
						"T2MA_15testP2", "T2MA_16testP2", };
				String[] vetPath = new InputOutput().findListTestImages2(
						MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

				boolean step = false;
				boolean typeT2 = true;

				ImageStack stack2 = stackOpener1(vetPath, typeT2);
				ImagePlus imp2 = new ImagePlus("newStack", stack2);
				UtilAyv.showImageMaximized(imp2);

				String xpos = MyConst.P20_X_ROI_TESTSIEMENS;
				String ypos = MyConst.P20_Y_ROI_TESTSIEMENS;
				int diam = MyConst.P20_DIAM_ROI;

				Roi[] vetRoi = automaticRoiPreparation(imp2, xpos, ypos, diam);

				int[] bMap = mapPreparation(imp2, vetRoi);

				double filtro = filterPreparation(imp2, vetRoi, step);

				imp111 = doCalculation(stack2, filtro, bMap, typeT2, silent);
				if (imp111 == null)
					MyLog.waitHere("imp111 == null");

				String[] info1 = { "a1", "a2", "a3", "a4", "a5", "a6", "a7",
						"a8" };
				ResultsTable rt1 = analyzeResultsImages(vetRoi, imp111, info1,
						typeT2, autoCalled, verbose, test, fast, silent);

				rt1.show("Results");
				MyLog.waitHere();

				double[] vetResults = UtilAyv.vectorizeResultsMultiple(rt1, 2);

				boolean ok = UtilAyv.verifyResults1(vetResults,
						referenceSiemens(), MyConst.P20_vetName);

				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				break;
			}
		} else {
			UtilAyv.noTest2();
		}
		return;
	}

	/**
	 * Siemens test image expected results
	 * 
	 * @return
	 */
	public static double[] referenceSiemens() {

		double m1 = 59.59740003754821;
		double d1 = 3.961381020749687;
		double m2 = 74.93121866636639;
		double d2 = 3.5595121692227956;
		double m3 = 114.61472226396391;
		double d3 = 4.517450908271077;
		double m4 = 60.73479788816428;
		double d4 = 5.287766872828344;
		double m5 = 93.95210439947587;
		double d5 = 3.368187479134457;
		double m6 = 160.10322107242632;
		double d6 = 4.424880716097857;
		double m7 = 97.85791512984264;
		double d7 = 3.726849560932785;
		double m8 = 136.16384392750413;
		double d8 = 4.704255261848893;
		double m9 = 149.333523762377;
		double d9 = 4.115680095831744;
		double m10 = 215.65120981916596;
		double d10 = 12.191159135391526;
		double m11 = 161.92869036710715;
		double d11 = 7.367099894616932;
		double m12 = 143.27333520333977;
		double d12 = 5.046688782226316;

		double[] vetReference = { m1, d1, m2, d2, m3, d3, m4, d4, m5, d5, m6,
				d6, m7, d7, m8, d8, m9, d9, m10, d10, m11, d11, m12, d12 };
		return vetReference;
	}

	/**
	 * Ge test image expected results
	 * 
	 * @return
	 */
	double[] referenceGe() {
		double t1 = 1679.260738831615;
		double t2 = 5.048954649732993;
		double t3 = 332.5957263094887;
		double t4 = -0.3480660610768231;
		double t5 = -0.1697929188035829;
		double t6 = -0.3311056141375085;
		double t7 = -0.08329463941307846;
		double t8 = 89.70727101038716;
		double t9 = 11.401898734177216;
		double t10 = 0;
		double t11 = 0;
		double t14 = 0;
		double[] vetReference = { t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11,
				t14 };
		return vetReference;
	}

	/***
	 * Si incarica di preparare lo stack da analizzare
	 * 
	 * @param vetPath
	 * @param typeT2
	 * @return
	 */
	public static ImagePlus imaPreparation(String[] vetPath, boolean typeT2) {
		ImageStack newStack = stackOpener1(vetPath, typeT2);
		ImagePlus imp1 = new ImagePlus("newStack", newStack);
		return imp1;
	}

	/****
	 * posizionamento manuale delle ROI sui gels e sul fondo
	 * 
	 * @param imp8
	 * @return
	 */

	public static Roi[] manualRoiPreparation(ImagePlus imp8) {

		int Rows = ReadDicom.readInt(ReadDicom.readDicomParameter(imp8,
				MyConst.DICOM_ROWS));
		int Columns = ReadDicom.readInt(ReadDicom.readDicomParameter(imp8,
				MyConst.DICOM_COLUMNS));

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp8, MyConst.DICOM_PIXEL_SPACING),
						2));

		int roi_diam = (int) ((double) MyConst.P20_DIAM_ROI
				* MyConst.P20_DIM_PIXEL_FOV_220 / dimPixel);

		Roi[] vetRoi = new Roi[MyConst.P20_N_GELS + 1];

		String saveVetXUpperLeftCornerRoiGels = Prefs.get("prefer.p2rmnGx",
				MyConst.P20_DEFAULT);

		String saveVetYUpperLeftCornerRoiGels = Prefs.get("prefer.p2rmnGy",
				MyConst.P20_DEFAULT);

		int[] vetXUpperLeftCornerRoiGels = UtilAyv.getPos2(
				saveVetXUpperLeftCornerRoiGels, Rows);

		int[] vetYUpperLeftCornerRoiGels = UtilAyv.getPos2(
				saveVetYUpperLeftCornerRoiGels, Columns);

		// qui ho la selezione manuale della posizione dei 12 gels

		for (int i1 = 0; i1 < vetRoi.length - 1; i1++) {

			// inserisco un controllo per impedire che si possa
			// avere una roi al di fuori dell'immagine
			int xRoi = vetXUpperLeftCornerRoiGels[i1];
			int yRoi = vetYUpperLeftCornerRoiGels[i1];
			if (xRoi < 0)
				xRoi = Columns / 2;
			if (xRoi + roi_diam > Columns)
				xRoi = Columns / 2;
			if (yRoi < 0)
				yRoi = Rows / 2;
			if (yRoi + roi_diam > Rows)
				yRoi = Rows / 2;

			imp8.setRoi(new OvalRoi(xRoi, yRoi, roi_diam, roi_diam));

			imp8.updateAndDraw();

			msgGelRoiPositioning(i1 + 1);

			ImageStatistics stat1 = imp8.getStatistics();
			vetXUpperLeftCornerRoiGels[i1] = (int) stat1.roiX;
			vetYUpperLeftCornerRoiGels[i1] = (int) stat1.roiY;
			vetRoi[i1] = imp8.getRoi();
		}

		// roi fondo
		int xRoi2 = vetXUpperLeftCornerRoiGels[vetRoi.length - 1];
		int yRoi2 = vetXUpperLeftCornerRoiGels[vetRoi.length - 1];

		if (xRoi2 < 0)
			xRoi2 = Columns / 2;
		if (xRoi2 + roi_diam > Columns)
			xRoi2 = Columns / 2;
		if (yRoi2 < 0)
			yRoi2 = Rows / 2;
		if (yRoi2 + roi_diam > Rows)
			yRoi2 = Rows / 2;

		int roi_diam2 = (int) ((double) MyConst.P20_DIAM_ROI
				* MyConst.P20_DIM_PIXEL_FOV_220 / dimPixel);

		// / NOTA BENE: qui andrebbe messo UtilAyv.backCalc

		boolean bstep = false;
		boolean circular = true;
		boolean selftest = false;

		ImageStatistics statFondo = ImageUtils.backCalc(xRoi2, yRoi2,
				roi_diam2, imp8, bstep, circular, selftest);

		// imp8.setRoi(new OvalRoi(xRoi2, yRoi2, roi_diam2, roi_diam2));

		// imp8.updateAndDraw();

		// msgBkgRoiPositioning();

		ImageStatistics stat2 = imp8.getStatistics();
		vetXUpperLeftCornerRoiGels[vetRoi.length - 1] = (int) stat2.roiX;
		vetYUpperLeftCornerRoiGels[vetRoi.length - 1] = (int) stat2.roiY;
		vetRoi[vetRoi.length - 1] = imp8.getRoi();

		saveVetXUpperLeftCornerRoiGels = UtilAyv.putPos2(
				vetXUpperLeftCornerRoiGels, Columns);
		saveVetYUpperLeftCornerRoiGels = UtilAyv.putPos2(
				vetYUpperLeftCornerRoiGels, Rows);

		Prefs.set("prefer.p2rmnGx", saveVetXUpperLeftCornerRoiGels);
		Prefs.set("prefer.p2rmnGy", saveVetYUpperLeftCornerRoiGels);

		return vetRoi;
	}

	public static Roi[] automaticRoiPreparation(ImagePlus imp1, String presetX,
			String presetY, int diam) {

		Overlay over1 = new Overlay();
		imp1.setOverlay(over1);

		int[] vetX = UtilAyv.getPos(presetX);
		int[] vetY = UtilAyv.getPos(presetY);
		int len = vetX.length;

		Roi[] vetRoi = new Roi[len];
		for (int i1 = 0; i1 < len; i1++) {
			imp1.setRoi(new OvalRoi(vetX[i1], vetY[i1], diam, diam));
			vetRoi[i1] = imp1.getRoi();
			over1.addElement(imp1.getRoi());
			over1.setStrokeColor(Color.green);
		}
		imp1.updateAndDraw();
		return vetRoi;
	}

	/***
	 * Ricerca automatica delle posizioni gels con AutoTreshold ed
	 * AnalyzeParticles
	 * 
	 * @param imp1
	 * @param diam2
	 * @param timeout
	 * @param demo
	 */
	public static Roi[] automaticRoiPreparation2(ImagePlus imp1, int diam2,
			int timeout, boolean demo) {

		Overlay over1 = new Overlay();
		imp1.setOverlay(over1);

		boolean noBlack = true;
		boolean noWhite = false;
		boolean doWhite = true;
		boolean doSet = false;
		boolean doLog = false;
		int numGels = 12;

		if (demo) {
			UtilAyv.showImageMaximized(imp1);
			MyLog.waitHere(listaMessaggi(0), debug, timeout);
		}

		ImagePlus imp2 = MyAutoThreshold.threshold(imp1, "Mean", noBlack,
				noWhite, doWhite, doSet, doLog);
		if (demo) {
			UtilAyv.showImageMaximized(imp2);
			MyLog.waitHere(listaMessaggi(1), debug, timeout);
		}

		IJ.run(imp2, "Invert", "");
		if (demo) {
			// UtilAyv.showImageMaximized(imp2);
			MyLog.waitHere(listaMessaggi(2), debug, timeout);
		}

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						2));
		int measCode = 512; // bounding rectangle
		int oldMeasure = UtilAyv.setMeasure(measCode);
		if (demo) {
			IJ.run(imp2, "Analyze Particles...",
					"size=50-1000 circularity=0.1-1.00 show=Outlines exclude");
			// String title = "Drawing of "+imp2.getShortTitle();
			// MyLog.waitHere("title= "+title);
			WindowManager.getCurrentWindow().maximize();

			// "size=50-1000 circularity=0.1-1.00 show=Outlines display exclude summarize");
		} else
			IJ.run(imp2, "Analyze Particles...",
					"size=50-1000 circularity=0.1-1.00");
		ResultsTable rt1 = ResultsTable.getResultsTable();
		if (demo) {
			MyLog.waitHere(listaMessaggi(3), debug, timeout);
			rt1.show("Results");
		}

		int xcol = rt1.getColumnIndex("BX");
		int ycol = rt1.getColumnIndex("BY");
		int wcol = rt1.getColumnIndex("Width");
		int hcol = rt1.getColumnIndex("Height");

		double[] vetX = rt1.getColumnAsDoubles(xcol);
		// MyLog.logVector(vetX, "vetX");

		double[] vetY = rt1.getColumnAsDoubles(ycol);
		// MyLog.logVector(vetY, "vetY");

		double[] vetW = rt1.getColumnAsDoubles(wcol);
		// MyLog.logVector(vetW, "vetW");

		double[] vetH = rt1.getColumnAsDoubles(hcol);
		// MyLog.logVector(vetH, "vetH");

		// UtilAyv.showImageMaximized(imp1);
		double[] vetx1 = new double[vetX.length];
		double[] vety1 = new double[vetX.length];
		for (int i1 = 0; i1 < vetX.length; i1++) {
			vetx1[i1] = ((vetX[i1] + vetW[i1] / 2) / dimPixel) - diam2 / 2;
			vety1[i1] = ((vetY[i1] + vetH[i1] / 2) / dimPixel) - diam2 / 2;
		}

		Roi[] vetRoi = new Roi[vetX.length];
		for (int i1 = 0; i1 < vetX.length; i1++) {
			imp1.setRoi(new OvalRoi(vetx1[i1], vety1[i1], diam2, diam2));
			vetRoi[i1] = imp1.getRoi();
			imp1.getRoi().setStrokeColor(Color.green);
			over1.addElement(imp1.getRoi());
		}
		if (vetX.length != numGels)
			demo = true;
		if (demo) {
			imp1.updateAndDraw();
			imp1.getWindow().toFront();
			MyLog.waitHere(listaMessaggi(4), debug, timeout);
			MyLog.waitHere(listaMessaggi(4), debug, timeout);
		}
		if (vetX.length != numGels)
			MyLog.waitHere("problema sul numero gels rilevati");

		return vetRoi;

	}


	public static ResultsTable analyzeResultsImages(Roi[] vetRoi,
			ImagePlus imp111, String[] info1, boolean typeT2,
			boolean autoCalled, boolean verbose, boolean test, boolean fast,
			boolean silent) {

		boolean accetta = false;
		ResultsTable rt = null;

		double[] medGels = new double[vetRoi.length];
		double[] devGels = new double[vetRoi.length];

		for (int i1 = 0; i1 < vetRoi.length; i1++) {

			ImagePlus imp10 = MyStackUtils.imageFromStack(imp111, 1);
			Rectangle rec1 = vetRoi[i1].getBounds();

			imp10.setRoi(new OvalRoi(rec1.x, rec1.y, rec1.width, rec1.height));

			imp10.updateAndDraw();
			ImageStatistics stat10 = imp10.getStatistics();
			medGels[i1] = stat10.mean;
			devGels[i1] = stat10.stdDev;
		}
		//
		// qui potrei anche chiudere le immagini
		//
		String t1 = "TESTO";

		do {
			rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);

			if (typeT2)
				rt.setLabel("T2___", 0);
			else
				rt.setLabel("T1___", 0);

			int gelNumber = 0;
			for (int i1 = 0; i1 < vetRoi.length - 1; i1++) {
				Rectangle rec1 = vetRoi[i1].getBounds();

				gelNumber++;
				if (gelNumber > 1)
					rt.incrementCounter();
				if (gelNumber == 12)
					gelNumber = 14; // al posto 12 abbiamo il gel 14

				rt.addLabel(t1, "Gel_" + gelNumber);
				rt.addValue("media", medGels[i1]);
				rt.addValue("devstan", devGels[i1]);
				rt.addValue("roi_x", rec1.x);
				rt.addValue("roi_y", rec1.y);
				rt.addValue("roi_b", rec1.width);
				rt.addValue("roi_h", rec1.height);
			}
			// rt.show("Results");

			if (verbose && !test && !fast) {
				rt.show("Results");
			}

			if (fast) {
				accetta = true;
			} else if (autoCalled && !test) {
				accetta = MyMsg.accettaMenu();
			} else {
				if (!test) {
					accetta = MyMsg.msgStandalone();
				} else {
					accetta = test;
				}

			}

		} while (!accetta);

		return rt;
	}

	/***
	 * 
	 * @param vetPath
	 * @param autoArgs
	 * @param typeT2
	 * @param autoCalled
	 * @param step
	 * @param verbose
	 * @param test
	 * @return
	 */

//	ResultsTable mainEchoCalculation(ImagePlus imp1, String autoArgs,
//			boolean typeT2, boolean autoCalled, boolean step, boolean verbose,
//			boolean test) {

//		ResultsTable rt = null;
//		boolean accetta = false;

//		int userSelection1 = 0;
//		int userSelection2 = 0;
//		int userSelection3 = 0;
//
//		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
//		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
//
//		int[] vetXUpperLeftCornerRoiGels = new int[MyConst.P20_N_GELS];
//		int[] vetYUpperLeftCornerRoiGels = new int[MyConst.P20_N_GELS];
//
//		String defaultVetXUpperLeftCornerRoiGels = MyConst.P20_X_ROI_TESTSIEMENS;
//		String defaultVetYUpperLeftCornerRoiGels = MyConst.P20_Y_ROI_TESTSIEMENS;
//
//		double dimPixel = 0;
//		double filtroFondo = 0;
//
//		double kMediaFiltroFondo = 3.0;
//		double kDevStFiltroFondo = 3.0;
//
//		InputOutput io = new InputOutput();
//
//		boolean retry = false;
//		AboutBox ab = new AboutBox();
//
//		int misure1 = UtilAyv.setMeasure(MEAN + STD_DEV);

		// String[][] info1 = ReportStandardInfo.getStandardInfo(strRiga3,
		// vetPath[0], tabl, VERSION, autoCalled);

		//
		// Qui si torna se la misura � da rifare
		//

		/*
		 * do { UtilAyv.closeResultsWindow();
		 * IJ.showStatus("Ricordiamo il comando RestoreSelection CTRL+SHIFT+E");
		 * 
		 * 
		 * 
		 * ImageStack newStack = stackOpener1(vetPath, typeT2); if (newStack ==
		 * null) return null; ImagePlus imp8 = new ImagePlus("newStack",
		 * newStack); if (imp8 == null) return null;
		 * UtilAyv.showImageMaximized(imp8);
		 * 
		 * int width = imp8.getWidth(); int height = imp8.getHeight();
		 * 
		 * int Rows = ReadDicom.readInt(ReadDicom.readDicomParameter(imp8,
		 * MyConst.DICOM_ROWS)); int Columns =
		 * ReadDicom.readInt(ReadDicom.readDicomParameter(imp8,
		 * MyConst.DICOM_COLUMNS));
		 * 
		 * dimPixel = ReadDicom.readDouble(ReadDicom.readSubstring(ReadDicom
		 * .readDicomParameter(imp8, MyConst.DICOM_PIXEL_SPACING), 2));
		 * 
		 * int roi_diam = (int) ((double) MyConst.P20_DIAM_ROI
		 * MyConst.P20_DIM_PIXEL_FOV_220 / dimPixel);
		 * 
		 * Roi[] vetRoi = new Roi[MyConst.P20_N_GELS];
		 * 
		 * String saveVetXUpperLeftCornerRoiGels = Prefs.get("prefer.p2rmnGx",
		 * defaultVetXUpperLeftCornerRoiGels); String
		 * saveVetYUpperLeftCornerRoiGels = Prefs.get("prefer.p2rmnGy",
		 * defaultVetYUpperLeftCornerRoiGels);
		 * 
		 * if (selftest) { saveVetXUpperLeftCornerRoiGels =
		 * defaultVetXUpperLeftCornerRoiGels; saveVetYUpperLeftCornerRoiGels =
		 * defaultVetYUpperLeftCornerRoiGels; }
		 * 
		 * if (!selftest) { vetXUpperLeftCornerRoiGels = UtilAyv.getPos2(
		 * saveVetXUpperLeftCornerRoiGels, Columns);
		 * 
		 * vetYUpperLeftCornerRoiGels = UtilAyv.getPos2(
		 * saveVetYUpperLeftCornerRoiGels, Rows); }
		 * 
		 * // qui ho la selezione manuale della posizione dei 12 gels
		 * 
		 * for (int i1 = 0; i1 < vetRoi.length; i1++) { if (!selftest) { do {
		 * 
		 * // inserisco un controllo per impedire che si possa // avere una roi
		 * al di fuori dell'immagine int xRoi = vetXUpperLeftCornerRoiGels[i1];
		 * int yRoi = vetYUpperLeftCornerRoiGels[i1]; if (xRoi < 0) xRoi =
		 * Columns / 2; if (xRoi + roi_diam > Columns) xRoi = Columns / 2; if
		 * (yRoi < 0) yRoi = Rows / 2; if (yRoi + roi_diam > Rows) yRoi = Rows /
		 * 2;
		 * 
		 * imp8.setRoi(new OvalRoi(xRoi, yRoi, roi_diam, roi_diam, imp8));
		 * 
		 * imp8.updateAndDraw(); userSelection1 = ButtonMessages.ModelessMsg(
		 * "Posizionare ROI su GEL" + (i1 + 1) +
		 * "  e premere Accetta      <08>", "ACCETTA", "RIDISEGNA"); } while
		 * (userSelection1 == 1); } else { imp8.setRoi(new
		 * OvalRoi(vetXUpperLeftCornerRoiGels[i1],
		 * vetYUpperLeftCornerRoiGels[i1], roi_diam, roi_diam, imp8));
		 * imp8.updateAndDraw(); MyLog.waitHere("verifica posizione"); // if
		 * (bstep) // userSelection1 = utils.ModelessMsg( //
		 * "Test non modificare <40>", "CONTINUA"); } ImageStatistics stat1 =
		 * imp8.getStatistics(); vetXUpperLeftCornerRoiGels[i1] = (int)
		 * stat1.roiX; vetYUpperLeftCornerRoiGels[i1] = (int) stat1.roiY;
		 * vetRoi[i1] = imp8.getRoi(); }
		 * 
		 * saveVetXUpperLeftCornerRoiGels = UtilAyv.putPos2(
		 * vetXUpperLeftCornerRoiGels, Columns); saveVetYUpperLeftCornerRoiGels
		 * = UtilAyv.putPos2( vetYUpperLeftCornerRoiGels, Rows);
		 * 
		 * // MyLog.waitHere("saveVetXUpperLeftCornerRoiGels = "+
		 * saveVetXUpperLeftCornerRoiGels); //
		 * MyLog.waitHere("saveVetYUpperLeftCornerRoiGels = "
		 * +saveVetYUpperLeftCornerRoiGels);
		 * 
		 * // saveVetXUpperLeftCornerRoiGels = ""; //
		 * saveVetYUpperLeftCornerRoiGels = ""; // for (int i1 = 0; i1 <
		 * vetXUpperLeftCornerRoiGels.length; i1++) { //
		 * saveVetXUpperLeftCornerRoiGels = saveVetXUpperLeftCornerRoiGels // +
		 * vetXUpperLeftCornerRoiGels[i1] + ";"; //
		 * saveVetYUpperLeftCornerRoiGels = saveVetYUpperLeftCornerRoiGels // +
		 * vetYUpperLeftCornerRoiGels[i1] + ";"; // }
		 * 
		 * Prefs.set("prefer.p2rmnGx", saveVetXUpperLeftCornerRoiGels);
		 * Prefs.set("prefer.p2rmnGy", saveVetYUpperLeftCornerRoiGels);
		 * 
		 * // ROI sul fondo
		 * 
		 * int xRoiFondo = Rows - DIAM_ROI_FONDO - 1; int yRoiFondo = Columns -
		 * DIAM_ROI_FONDO - 1; ImageStatistics statFondo =
		 * ImageUtils.backCalc(xRoiFondo, yRoiFondo, DIAM_ROI_FONDO, imp8,
		 * bstep, true, selftest); double mediaFondo = statFondo.mean; double
		 * dsFondo = statFondo.stdDev; filtroFondo = mediaFondo *
		 * kMediaFiltroFondo + dsFondo kDevStFiltroFondo;
		 * 
		 * bMap = new int[width * height]; int offset; for (int y = 0; y <
		 * height; y++) { offset = y * width; for (int x = 0; x < width; x++) {
		 * bMap[x + offset] = 0; for (int i1 = 0; i1 < vetRoi.length; i1++) if
		 * (vetRoi[i1].contains(x, y)) bMap[x + offset] = 1; } } int[] imaID =
		 * doCalculation(newStack, filtroFondo); if (imaID == null)
		 * ButtonMessages .ModelessMsg("errore p2_rmn riga 523", "continua");
		 * 
		 * double[] medGels = new double[vetXUpperLeftCornerRoiGels.length];
		 * double[] devGels = new double[vetXUpperLeftCornerRoiGels.length];
		 * 
		 * for (int i1 = 0; i1 < vetXUpperLeftCornerRoiGels.length; i1++) {
		 * ImagePlus imp10 = WindowManager.getImage(imaID[0]); imp10.setRoi(new
		 * OvalRoi(vetXUpperLeftCornerRoiGels[i1],
		 * vetYUpperLeftCornerRoiGels[i1], roi_diam, roi_diam, imp10));
		 * imp10.updateAndDraw(); ImageStatistics stat10 =
		 * imp10.getStatistics(); medGels[i1] = stat10.mean; devGels[i1] =
		 * stat10.stdDev; } // // qui potrei anche chiudere le immagini //
		 * String t1 = "TESTO          "; rt =
		 * ReportStandardInfo.putStandardInfoRT(info1); int col = 0;
		 * rt.setHeading(++col, t1); rt.setHeading(++col, "media");
		 * rt.setHeading(++col, "devstan"); rt.setHeading(++col, "roi_x");
		 * rt.setHeading(++col, "roi_y"); rt.setHeading(++col, "roi_b");
		 * rt.setHeading(++col, "roi_h"); if (typeT2) rt.setLabel("T2___", 0);
		 * else rt.setLabel("T1___", 0);
		 * 
		 * int gelNumber = 0; for (int i1 = 0; i1 < vetRoi.length; i1++) {
		 * gelNumber++; if (gelNumber > 1) rt.incrementCounter(); if (gelNumber
		 * == 12) gelNumber = 14; // al posto 12 abbiamo il gel 14
		 * 
		 * rt.addLabel(t1, "Gel_" + gelNumber); rt.addValue(2, medGels[i1]);
		 * rt.addValue(3, devGels[i1]); rt.addValue(4,
		 * vetXUpperLeftCornerRoiGels[i1]); rt.addValue(5,
		 * vetYUpperLeftCornerRoiGels[i1]); rt.addValue(6, roi_diam);
		 * rt.addValue(7, roi_diam); } rt.show("Results");
		 * 
		 * if (autoCalled) { userSelection3 = ButtonMessages.ModelessMsg(
		 * "Accettare il risultato delle misure?     <11>", "ACCETTA", "RIFAI");
		 * switch (userSelection3) { case 1: UtilAyv.cleanUp(); accetta = false;
		 * break; case 2: accetta = true; break; } } if (manualCalled) { if
		 * (selftest) { if (testSiemens) testSymphony(medGels[0], devGels[0],
		 * medGels[1], devGels[1], medGels[2], devGels[2]);
		 * 
		 * if (testGe) testGe(medGels[0], devGels[0], medGels[1], devGels[1],
		 * medGels[2], devGels[2]); } else { userSelection3 = ButtonMessages
		 * .ModelessMsg(
		 * "Fine programma, in modo STAND-ALONE salvare A MANO la finestra Risultati    <12>"
		 * , "CONTINUA"); System.gc();
		 * 
		 * } accetta = true; } } while (!accetta); // do
		 * 
		 * // // Salvataggio dei risultati //
		 * 
		 * if (autoCalled) {
		 * 
		 * try { mySaveAs(fileDir + MyConst.TXT_FILE); } catch (IOException e) {
		 * // TODO Auto-generated catch block e.printStackTrace(); }
		 * 
		 * // IJ.run("Excel...", "select...=[" + fileDir + XLS_FILE + "]");
		 * TableSequence lr = new TableSequence(); for (int i1 = 0; i1 <
		 * nTokens; i1++) lr.putDone(strRiga3, vetRiga1[i1]);
		 * lr.writeTable(fileDir + SEQUENZE_FILE, strRiga3); }
		 * UtilAyv.resetResultsTable(); UtilAyv.resetMeasure(misure1);
		 * 
		 * InputOutput.deleteDir(new File(TEST_DIRECTORY)); if (autoCalled)
		 * UtilAyv.cleanUp(); return rt;
		 */
//		return null;
//	}
//
	/**
	 * Saves this ResultsTable as a tab or comma delimited text file. The table
	 * is saved as a CSV (comma-separated values) file if 'path' ends with
	 * ".csv". Displays a file save dialog if 'path' is empty or null. Does
	 * nothing if the table is empty.
	 */
	public static void mySaveAs(String path) throws IOException {

		ResultsTable rt = ResultsTable.getResultsTable();
		if (rt.getCounter() == 0)
			return;
		PrintWriter pw = null;
		boolean append = true;
		FileOutputStream fos = new FileOutputStream(path, append);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		pw = new PrintWriter(bos);
		String headings = rt.getColumnHeadings();
		pw.println("## " + headings);
		for (int i = 0; i < rt.getCounter(); i++)
			pw.println(rt.getRowAsString(i));
		pw.close();
	}

	/**
	 * crea uno stack contenente le immagini di vetDir1, in ordine crescente
	 * rispetto al parametro selezionato
	 * 
	 * @param vetDir1
	 *            vettore coi path immagini
	 * @param typeT2
	 *            selezione parametro ordinamento
	 * @return stack con le immagini
	 * 
	 *         by Alberto Duina
	 */
	public static ImageStack stackOpener1(String[] vetDir1, boolean typeT2) {

		String aux1;
		String aux2;

		ImageStack newStack1 = null;
		int count1 = 0;
		String[] para1 = new String[vetDir1.length];
		for (int i1 = 0; i1 < vetDir1.length; i1++) {
			ImagePlus imp1 = new Opener().openImage(vetDir1[i1]);
			if (typeT2) {
				aux1 = MyConst.DICOM_ECHO_TIME;
			} else {
				aux1 = MyConst.DICOM_INVERSION_TIME;
			}
			aux2 = ReadDicom.readDicomParameter(imp1, aux1);
			if (aux2.compareTo("MISS") == 0) {
				ButtonMessages.ModelessMsg(
						"Errore selezione tipo immagine T1 o T2 " + aux1,
						"CONTINUA");
				return (null);
			}
			para1[i1] = aux2;
		}

		/** bubblesort start */
		double d1;
		double d2;
		String temp1;
		for (int i1 = 0; i1 < vetDir1.length; i1++) {
			for (int i2 = 0; i2 < vetDir1.length - 1 - i1; i2++) {
				d1 = ReadDicom.readDouble(para1[i2]);
				d2 = ReadDicom.readDouble(para1[i2 + 1]);
				if (d1 > d2) {
					temp1 = para1[i2];
					para1[i2] = para1[i2 + 1];
					para1[i2 + 1] = temp1;

					temp1 = vetDir1[i2];
					vetDir1[i2] = vetDir1[i2 + 1];
					vetDir1[i2 + 1] = temp1;
				}
			}
		}
		/** bubblesort end */
		for (int i1 = 0; i1 < vetDir1.length; i1++) {
			count1++;
			ImagePlus imp1 = new Opener().openImage(vetDir1[i1]);
			if (count1 == 1) {
				int Rows = ReadDicom.readInt(ReadDicom.readDicomParameter(imp1,
						MyConst.DICOM_ROWS));
				int Columns = ReadDicom.readInt(ReadDicom.readDicomParameter(
						imp1, MyConst.DICOM_COLUMNS));
				// cal8 = imp1.getCalibration();
				newStack1 = new ImageStack(Rows, Columns);
			}
			String label1 = imp1.getTitle();
			String info2 = (String) imp1.getProperty("Info");
			if (info2 != null)
				label1 += "\n" + info2;
			ImageProcessor ip1 = imp1.getProcessor();
			ImageProcessor ip2 = ip1.duplicate();
			newStack1.addSlice(label1, ip2);
		}
		return (newStack1);
	} // stackOpener1

	/**
	 * dispone il contenuto dei pixel nella tabella in formato double [][][][].
	 * Nellla tabella abbiamo: tabella[rep][slice][x][y] in cui slice � sempre 0
	 * (usato per compatibilit� con le librerie che sono fatte per processare
	 * dei volumi.
	 * 
	 * @param stack1
	 *            stack di immagini da processare
	 * @return valori dei pixel nel formato double[a][b][c][d], in cui a=posiz.
	 *         immagine nello stack b=dummy sempre 0, c=coord. x pixel, d=coord.
	 *         y pixel
	 * 
	 *         by Alberto Duina
	 */

	public static double[][][][] Alimentatore(ImageStack stack1) {

		String name1 = "";
		ImagePlus imp1 = new ImagePlus(name1, stack1);

		int[] dims = new int[MyConst.P20_ARRAY_DIMENSIONS];
		dims[0] = imp1.getHeight();
		dims[1] = imp1.getWidth();
		dims[2] = 1;
		dims[3] = imp1.getStackSize();
		int slice = 0;
		double pixValue = 0;

		double[][][][] ret = new double[dims[3]][dims[2]][dims[1]][dims[0]];

		Calibration cal8 = imp1.getCalibration();

		for (int rep = 0; rep < ret.length; rep++) {
			ImageProcessor ip1 = stack1.getProcessor(rep + 1);
			ip1.setCalibrationTable(cal8.getCTable());
			int width2 = ip1.getWidth();
			int height2 = ip1.getHeight();
			short[] sdata = (short[]) ip1.getPixels();
			for (int y = 0; y < height2; y++) {
				for (int x = 0; x < width2; x++) {
					// pixValue serve per leggere correttamente i valori delle
					// immagini signed (calibrate, GE)
					pixValue = (double) cal8.getRawValue(sdata[x + y * width2]);
					ret[rep][slice][x][y] = pixValue;
				}
			}
		}
		return (ret);
	} // Alimentatore

	/**
	 * legge un valore nell'header delle immagini contenute nello stack e lo
	 * restituisce in un vettore
	 * 
	 * @param stack1
	 *            stack immagini da processare
	 * @param userinput
	 *            parametro da leggere
	 * @return vettore col TR / TI
	 * 
	 *         by Alberto Duina
	 */
	private static double[] getTRVals(ImageStack stack1, String userinput) {
		//
		// attenzione nel caso del T1, viene chiamato TR ma in realt� � il TI
		//
		String attribute = "???";
		String value = "???";

		int nFrames = stack1.getSize();
		double[] ret = new double[nFrames];
		for (int w1 = 0; w1 < nFrames; w1++) {
			String header = stack1.getSliceLabel(w1 + 1);
			if (header != null) {
				int idx1 = header.indexOf(userinput);
				int idx2 = header.indexOf(":", idx1);
				int idx3 = header.indexOf("\n", idx2);
				if (idx1 >= 0 && idx2 >= 0 && idx3 >= 0) {
					try {
						attribute = header.substring(idx1 + 9, idx2).trim();
						value = header.substring(idx2 + 1, idx3).trim();

						ret[w1] = ReadDicom.readDouble(value);
					} catch (Throwable e) {

						MyLog.waitThere("getTRVals error value >> " + value);
					}
				} else {
					attribute = "MISSING";
					MyLog.waitThere("getTRVals error attribute >> " + attribute);
				}
			} else {
				attribute = "HEADERNULL";
				MyLog.waitThere("getTRVals error attribute >> " + attribute);
			}

		}
		return ret;

	}

	/**
	 * main che effettua la minimizzazione
	 * 
	 * @param stack4
	 *            stack contenente le immagini
	 * @return ID delle immagini da passare a WindowManager
	 * 
	 *         by Karl Schmidt, (modified version)
	 */
	public static ImagePlus doCalculation(ImageStack stack4,
			double filtroFondo, int[] bMap, boolean typeT2, boolean silent) {
		double[][][][] t1_map = null;
		double[][][][] s0_map = null;
		double[][][][] r2_map = null;

		double progress = 0;
		double[] tr_vals = null;
		double[] sn_vals = null;
		double[] tr2_vals = null;
		double[] sn2_vals = null;

		int rig = 0;
		int col = 0;
		int offset;
		int shot = 0;
		int shotInterval = 0;
		ImageStack newStack = null;

		try {
			double[][][][] scan = Alimentatore(stack4);
			int slices = scan[0].length;
			int width2 = scan[0][0].length;
			int height2 = scan[0][0][0].length;
			int reps = scan.length;

			SimplexBasedRegressor simplexregressor;
			if (DEBUG2)
				IJ.log("sono in doCalculation");
			if (!typeT2) {
				// te values
				tr_vals = getTRVals(stack4, MyConst.DICOM_INVERSION_TIME);
				sn_vals = new double[reps];
				IJ.showStatus("Calculating T1 map");
				t1_map = new double[1][slices][width2][height2];
				s0_map = new double[1][slices][width2][height2];
				r2_map = new double[1][slices][width2][height2];
				simplexregressor = new SimplexBasedRegressor();
			} else {
				tr_vals = getTRVals(stack4, MyConst.DICOM_ECHO_TIME);
				sn_vals = new double[reps];
				IJ.showStatus("Calculating T2 map");
				t1_map = new double[1][slices][width2][height2];
				s0_map = new double[1][slices][width2][height2];
				r2_map = new double[1][slices][width2][height2];
				simplexregressor = new SimplexBasedRegressor();
			}
			// fit T2 and So
			shot = 0;
			shotInterval = 100;

			for (int s = 0; s < slices; s++) {
				for (int y = 0; y < height2; y++) {
					offset = y * width2;
					rig = y;
					progress = (double) (100 * (s * width2 * height2 + y
							* width2) / (width2 * height2 * slices));
					IJ.showStatus("riga:" + y);
					IJ.showProgress(progress);
					for (int x = 0; x < width2; x++) {
						if ((x == 47 || x == 46 || x == 48)
								&& (y == 88 || y == 87 || y == 89)) {
							if (DEBUG2 == true)
								debug1 = true;
						} else {
							debug1 = false;
						}

						IJ.showProgress(progress);
						col = x;
						// for each pixel, collect values for each te
						for (int r = 0; r < reps; r++) {
							sn_vals[r] = scan[r][s][x][y];
							if (sn_vals[r] == 0)
								sn_vals[r] = 1.0d;
						}

						double[] regressed;
						// if (!typeT2 || (sn_vals.length < 10)) {
						if (!typeT2) {
							if (bMap[x + offset] == 1) {
								regressed = simplexregressor
										.regressT1InversionRecovery(tr_vals,
												sn_vals, 700.0d);

								t1_map[0][s][x][y] = regressed[0];
								s0_map[0][s][x][y] = regressed[1];
								r2_map[0][s][x][y] = regressed[2];
							} else {
								t1_map[0][s][x][y] = 0;
								s0_map[0][s][x][y] = 0;
								r2_map[0][s][x][y] = 0;
							}
						} else {
							if (bMap[x + offset] == 1) {
								int cut = 0;
								double filtroFondo2 = filtroFondo;
								// ##############################################
								// applicazione del filtro sul fondo ai segnali
								// in ingresso, ma solo se abbiamo pi� di 10
								// immagini
								// #############################################

								if (sn_vals.length < 10)
									filtroFondo2 = -32768;
								if (selftest)
									filtroFondo2 = -32768;
								for (int i1 = 0; i1 < sn_vals.length; i1++) {
									if (sn_vals[i1] > filtroFondo2) {
										cut = i1;
										// break;
									}
								}

								cut++;
								String bb = "";
								tr2_vals = new double[cut];
								sn2_vals = new double[cut];
								for (int i1 = 0; i1 < cut; i1++) {
									tr2_vals[i1] = tr_vals[i1];
									sn2_vals[i1] = sn_vals[i1];
								}

								// ########## debug ##########
								boolean xx = false;
								if (shot++ > shotInterval) {
									shot = 0;
									xx = true;
								} else
									xx = false;
								xx = false; // debug spento
								// if (xx) {
								if (debug1) {

									String aa = "";
									for (int i1 = 0; i1 < tr_vals.length; i1++) {
										aa = aa + tr_vals[i1] + "  ";
										bb = bb + sn_vals[i1] + "  ";
									}
									IJ.log("-----------------");
									IJ.log("tr_vals= " + aa);
									IJ.log("sn_vals= " + bb);
									IJ.log("filtroFondo2= " + filtroFondo2);
									IJ.log("cut= " + cut);
									aa = "";
									bb = "";
									for (int i1 = 0; i1 < tr2_vals.length; i1++) {
										aa = aa + tr2_vals[i1] + "  ";
										bb = bb + sn2_vals[i1] + "  ";
									}
									IJ.log("tr2_vals= " + aa);
									IJ.log("sn2_vals= " + bb);
								}
								// ###################################

								//
								// impongo che i valori su cui effettuare il fit
								// siano almeno 4 ..... insisto che dissento
								// profondamente su filtro del fondo
								// ... quasi un dissenteria!!
								//
								if (cut >= 4) {
									// regressed = simplexregressor.regressT2(
									// tr2_vals, sn2_vals, 150.0d, debug1);
									regressed = simplexregressor.regressT2(
											tr2_vals, sn2_vals, 150.0d, false);
									if (debug1) {
										IJ.log("T2calculated= " + regressed[0]);
										IJ.log("S0calculated= " + regressed[1]);
										IJ.log("R2calculated= " + regressed[2]);
									}
									t1_map[0][s][x][y] = regressed[0];
									s0_map[0][s][x][y] = regressed[1];
									r2_map[0][s][x][y] = regressed[2];
								} else {
									t1_map[0][s][x][y] = 0;
									s0_map[0][s][x][y] = 0;
									r2_map[0][s][x][y] = 0;
								}

							} else {
								t1_map[0][s][x][y] = 0;
								s0_map[0][s][x][y] = 0;
								r2_map[0][s][x][y] = 0;
							}
						}
						// IJ.showProgress(progress);
					}
				}

				IJ.showStatus("Finished");
				// send the images back to imagej
				String name1 = "none";

				// imaID = new int[3];
				if (!typeT2)
					name1 = "T1_map_secs";
				else
					name1 = "T2_map_secs";

				ImagePlus imp100 = takeImage(name1, t1_map);
				ImagePlus imp101 = takeImage("S0_map", s0_map);
				ImagePlus imp102 = takeImage("R^2_map", r2_map);

				int width1 = imp100.getWidth();
				int height1 = imp100.getHeight();
				newStack = new ImageStack(width1, height1);
				ImageProcessor ip100 = imp100.getProcessor();
				newStack.update(ip100);
				newStack.addSlice(ip100);
				newStack.addSlice(imp101.getProcessor());
				newStack.addSlice(imp102.getProcessor());

				scan = null;
				// System.gc();
			}
		} catch (Exception e) {
			IJ.log("riga:" + rig + "  pixel:" + col);
			IJ.log("sn_vals=" + sn_vals.length);
			for (int i1 = 0; i1 < sn_vals.length; i1++)
				IJ.log("" + sn_vals[i1]);
			IJ.log("In DoCalculation eccezione: " + e);
		}
		ImagePlus imp111 = new ImagePlus("", newStack);
		return imp111;
	}

	/**
	 * crea uno stack a 32 bit in cui mette i risultati
	 * 
	 * @param name
	 *            nome da assegnare allo stack
	 * @param data4d
	 *            matrice risultati elaborazione
	 * @return ID immagine
	 * 
	 *         by Karl Schmidt, (modified version)
	 */
	public static ImagePlus takeImage(String name, double[][][][] data4d) {
		// create a new stack of 32 bit floating point pixels
		ImagePlus newimp = NewImage.createFloatImage(name, data4d[0][0].length,
				data4d[0][0][0].length, data4d.length * data4d[0].length,
				NewImage.FILL_BLACK);
		ImageStack stack = newimp.getStack();

		// populate the slices of the new stack with the transform
		for (int rep = 0; rep < data4d.length; rep++) {
			for (int slice = 0; slice < data4d[0].length; slice++) {
				// set the processor to the 2d data array
				setPixels(stack, data4d[rep][slice], rep * data4d[0].length + 1
						+ slice, ImagePlus.GRAY32);
			}
		}

		newimp.setTitle(name);
		// if (!silent)
		// newimp.show();

		// set this images dimensions
		int[] dims = new int[MyConst.P20_ARRAY_DIMENSIONS];
		dims[0] = data4d[0][0].length;
		dims[1] = data4d[0][0][0].length;
		dims[2] = data4d[0].length;
		dims[3] = data4d.length;

		setImageDims(newimp.getID(), dims);
		// newimp.updateAndDraw();
		newimp.getProcessor().resetMinAndMax();
		// IJ.showMessage("Finito takeImage");
		return newimp;
	}

	/**
	 * inserisce nei pixel delle immagini dello stack i valori della matrice
	 * dati
	 * 
	 * @param stack
	 *            stack immagini
	 * @param data
	 *            dati da inserire
	 * @param slice
	 *            numero immagine nello stack
	 * @param type
	 *            numero livelli colore
	 * 
	 *            by Karl Schmidt, (modified version)
	 */
	private static void setPixels(ImageStack stack, double[][] data, int slice,
			int type) {
		int w = data.length;
		int h = data[0].length;
		ImageProcessor ip = stack.getProcessor(slice);
		if (type == ImagePlus.GRAY8) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					((byte[]) ip.getPixels())[x + y * w] = (byte) data[x][y];
				}
			}
		} else if (type == ImagePlus.GRAY16) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					((short[]) ip.getPixels())[x + y * w] = (short) data[x][y];
				}
			}
		} else if (type == ImagePlus.GRAY32) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					((float[]) ip.getPixels())[x + y * w] = (float) data[x][y];
				}
			}
		} else if (type == ImagePlus.COLOR_256 || type == ImagePlus.COLOR_RGB) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					((int[]) ip.getPixels())[x + y * w] = (int) data[x][y];
				}
			}
		}

	}

	/**
	 * sets the number of slices for a particular image in system memory for
	 * retreival later by other plugins [width, height, slices, reps]
	 * 
	 * @param id
	 *            ID dell'immagine
	 * @param dims
	 *            dimensioni matrice immagine
	 */
	public static void setImageDims(int id, int[] dims) {
		System.setProperty("imagej.imgslicenum." + id, "" + dims[2]);
	}

	// private void testSymphony(double medGel1, double devGel1, double medGel2,
	// double devGel2, double medGel3, double devGel3) {
	//
	//
	//
	//
	//
	// //
	// // aggiornato 05 novembre 2014
	// //
	// boolean testok = true;
	// double rightValue = 59.59740003754821; // 59.100444600551945;
	// if (medGel1 != rightValue) {
	// IJ.log("medGel1 ERRATA " + medGel1 + " anzich� " + rightValue);
	// testok = false;
	// }
	// rightValue = 3.961381020749687; // 4.006797467473977;
	// if (devGel1 != rightValue) {
	// IJ.log("devGel1 ERRATA " + devGel1 + " anzich� " + rightValue);
	// testok = false;
	// }
	// rightValue = 74.93121866636639; // 73.95285294931146;
	// if (medGel2 != rightValue) {
	// IJ.log("medGel2 ERRATA " + medGel2 + " anzich� " + rightValue);
	// testok = false;
	// }
	// rightValue = 3.5595121692227956; // 3.6103799208252223;
	// if (devGel2 != rightValue) {
	// IJ.log("devGel2 ERRATA " + devGel2 + " anzich� " + rightValue);
	// testok = false;
	// }
	// rightValue = 114.61472226396391; // 113.45177367970913;
	// if (medGel3 != rightValue) {
	// IJ.log("medGel3 ERRATA " + medGel3 + " anzich� " + rightValue);
	// testok = false;
	// }
	// rightValue = 4.517450908271077; // 4.585334675446435;
	// if (devGel3 != rightValue) {
	// IJ.log("devGel3 ERRATA " + devGel3 + " anzich� " + rightValue);
	// testok = false;
	// }
	//
	// if (testok)
	// ButtonMessages.ModelessMsg("Fine SelfTest TUTTO OKEY   <25>",
	// "CONTINUA");
	// else
	// ButtonMessages.ModelessMsg(
	// "Fine SelfTest CON ERRORI, vedi Log   <26>", "CONTINUA");
	// UtilAyv.cleanUp();
	//
	// }
	//
	// private void testGe(double medGel1, double devGel1, double medGel2,
	// double devGel2, double medGel3, double devGel3) {
	// //
	// // aggiornato 16 aprile 2008 1.40e
	// //
	//
	// boolean testok = true;
	//
	// double rightValue = 41.12338699871981;
	// if (medGel1 != rightValue) {
	// IJ.log("medGel1 ERRATA " + medGel1 + " anzich� " + rightValue);
	// testok = false;
	// }
	// rightValue = 6.807091233248218;
	// if (devGel1 != rightValue) {
	// IJ.log("devGel1 ERRATA " + devGel1 + " anzich� " + rightValue);
	// testok = false;
	// }
	// rightValue = 47.02839973304845;
	// if (medGel2 != rightValue) {
	// IJ.log("medGel2 ERRATA " + medGel2 + " anzich� " + rightValue);
	// testok = false;
	// }
	// rightValue = 6.160207177522188;
	// if (devGel2 != rightValue) {
	// IJ.log("devGel2 ERRATA " + devGel2 + " anzich� " + rightValue);
	// testok = false;
	// }
	// rightValue = 73.95455746711055;
	// if (medGel3 != rightValue) {
	// IJ.log("medGel3 ERRATA " + medGel3 + " anzich� " + rightValue);
	// testok = false;
	// }
	// rightValue = 5.076411495907888;
	// if (devGel3 != rightValue) {
	// IJ.log("devGel3 ERRATA " + devGel3 + " anzich� " + rightValue);
	// testok = false;
	// }
	//
	// if (testok)
	// ButtonMessages.ModelessMsg("Fine SelfTest TUTTO OKEY   <25>",
	// "CONTINUA");
	// else
	// ButtonMessages.ModelessMsg(
	// "Fine SelfTest CON ERRORI, vedi Log   <26>", "CONTINUA");
	// UtilAyv.cleanUp();
	//
	// }

	/**
	 * genera una directory temporanea e vi estrae le immagini di test da
	 * test2.jar
	 * 
	 * @return home1 path della directory temporanea con le immagini di test
	 */
	private String findTestImages() {
		// InputOutput io = new InputOutput();
		//
		// for (int i1 = 0; i1 < T2_TEST_IMAGES; i1++) {
		// if (i1 < 9)
		// io.extractFromJAR(TEST_FILE, "T2MA_0" + (i1 + 1) + "testP2",
		// "./Test2/");
		// else
		// io.extractFromJAR(TEST_FILE, "T2MA_" + (i1 + 1) + "testP2",
		// "./Test2/");
		// }
		// for (int i1 = 0; i1 < T1_TEST_IMAGES; i1++) {
		// io.extractFromJAR(TEST_FILE, "HT1A2_0" + (i1 + 1) + "testP2",
		// "./Test2/");
		// }
		// String home1 = this.getClass().getResource(TEST_DIRECTORY).getPath();
		// return (home1);
		return null;
	} // findTestImages

	private static void msgGelRoiPositioning(int num) {
		ButtonMessages.ModelessMsg("Posizionare la Roi sul gel " + num
				+ " e premere CONTINUA", "CONTINUA");
	}

	private static void msgBkgRoiPositioning() {
		ButtonMessages.ModelessMsg(
				"Posizionare la Roi sul FONDO e premere CONTINUA", "CONTINUA");
	}

	public static boolean msgT1T2() {
		int preset=2;
		int timeout=100;
		int userSelection1 = ButtonMessages.ModelessMsg("Tipo elaborazione",
				"immagini T2", "immagini T1", preset, timeout);

		if (userSelection1 == 2)
			return true;
		else
			return false;
	}

	/**
	 * Qui sono raggruppati tutti i messaggi del plugin, in questo modo �
	 * facilitata la eventuale modifica / traduzione dei messaggi.
	 * 
	 * @param select
	 * @return
	 */
	public static String listaMessaggi(int select) {
		String[] lista = new String[100];
		// ---------+-----------------------------------------------------------+
		lista[0] = "L'immagine in input viene processata con AutoThreshold.threshold+Median";
		lista[1] = "L'immagine binaria viene invertita";
		lista[2] = "Viene utilizzato Analyze Particles (size=50-1000 circularity=0.1-1.00)\n "
				+ "per identificare i gel e misurarne misurare i Bounding Rectangles";
		lista[3] = "Misurando i BoundingRectangles otteniamo questi risultati";
		lista[4] = "Posizione ROI in verde";
		lista[5] = "L'immagine in input viene processata con AutoThreshold.threshold+Huang";
		lista[6] = "Numero nuovi oggetti localizzati= ";
		lista[7] = "Numero ROI rilevate errato, posizioni ROI in verde";

		// ---------+-----------------------------------------------------------+
		String out = lista[select];
		return out;
	}

} // p2rmn
