package contMensili;

import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.StringTokenizer;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.NewImage;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyConst;
import utils.MyFileLogger;
import utils.MyLog;
import utils.MyMsg;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableExpand;
import utils.TableSequence;
import utils.UtilAyv;

/*
 * Copyright (C) 2017 Alberto Duina, Brescia ITALY
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
 * Analizza UNIFORMITA' RETTANGOLARE, SNR, GHOSTS.
 *
 * +++++++++++++++++ MODIFICHE CARTELLA SIMULATE ++++++++++++++++++++++
 *
 *
 * @author Alberto Duina
 *
 */

public class p19rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "UNIFORMITA' SNR GHOSTS per bobine RETTANGOLARI manuale";

	private static String TYPE = " >> CONTROLLO UNIFORMITA'RETTANGOLARE";

	private static String fileDir = "";
	public static String simpath = "";



	private static boolean debug = true;
	private static boolean mylogger = true;

	@Override
	public void run(String args) {

		UtilAyv.setMyPrecision();

		if (IJ.versionLessThan("1.43k")) {
			return;
		}

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
		String user1 = System.getProperty("user.name");
		TableCode tc1 = new TableCode();
		String iw2ayv1 = tc1.nameTable("codici", "csv");
		TableExpand tc2 = new TableExpand();
		String iw2ayv2 = tc1.nameTable("expand", "csv");

		VERSION = user1 + ":" + className + "build_" + MyVersion.getVersion() + ":iw2ayv_build_"
				+ MyVersionUtils.getVersion() + ":" + iw2ayv1 + ":" + iw2ayv2;

//		VERSION = className + "_build_" + MyVersion.getVersion() + "_iw2ayv_build_" + MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");
		if (mylogger) {
			MyFileLogger.logger.info("p13rmn_>>> fileDir= " + fileDir);
		}
		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}
		return;
	}

	/**
	 * Manual menu, invoked directly from the ImageJ plugins/ContMensili/p3rmn_
	 *
	 * @param preset
	 * @param testDirectory
	 * @return
	 */
	public int manualMenu(int preset, String testDirectory) {
		boolean retry = false;
		boolean step = false;
		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox().about("Controllo Uniformita'",
				// this.getClass());
				new AboutBox().about("Controllo Uniformita'", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				selfTestMenu();
				retry = true;
				break;
			case 4:
				step = true;
			case 5:
				String path1 = UtilAyv.imageSelection("SELEZIONARE PRIMA IMMAGINE...");
				if (path1 == null) {
					return 0;
				}
				String path2 = UtilAyv.imageSelection("SELEZIONARE SECONDA IMMAGINE...");
				if (path2 == null) {
					return 0;
				}
				boolean autoCalled = false;
				boolean verbose = true;
				boolean test = false;
				prepUnifor(path1, path2, "0", autoCalled, step, verbose, test);
				UtilAyv.afterWork();
				retry = true;
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	/**
	 * Auto menu invoked from Sequenze_
	 *
	 * @param autoArgs
	 * @return
	 */
	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p19 riceve " + autoArgs);

		// the autoArgs are passed from Sequenze_
		// possibilities:
		// 1 token -1 = silentAutoTest
		// 2 tokens auto
		// 4 tokens auto
		//

		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);

		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p19rmn_");
			selfTestSilent();
			return 0;
		}

		if ((nTokens != MyConst.TOKENS2) && (nTokens != MyConst.TOKENS4)) {
			MyMsg.msgParamError();
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);

		String path1 = "";
		String path2 = "";
		simpath = fileDir + "SIMULATE";
		File newdir3 = new File(simpath);
		boolean ok3 = false;
		boolean ok4 = false;
		if (newdir3.exists()) {
//			ok3 = InputOutput.deleteDir(newdir3);
//			if (!ok3)
//				MyLog.waitHere("errore cancellazione directory " + newdir3);
		} else {
			ok4 = InputOutput.createDir(newdir3);
			if (!ok4) {
				MyLog.waitHere("errore creazione directory " + newdir3);
			}
		}


		if (nTokens == MyConst.TOKENS2) {
			// UtilAyv.checkImages(vetRiga, iw2ayvTable, 2, debug);
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			UtilAyv.checkImages2(path1, path2, debug);

			MyLog.logDebug(vetRiga[0], "P19", fileDir);
			MyLog.logDebug(vetRiga[1], "P19", fileDir);

		} else {
			// UtilAyv.checkImages(vetRiga, iw2ayvTable, 3, debug);
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
			UtilAyv.checkImages2(path1, path2, debug);

			MyLog.logDebug(vetRiga[0], "P19", fileDir);
			MyLog.logDebug(vetRiga[2], "P19", fileDir);
		}

		boolean retry = false;
		boolean step = false;
		do {
			// int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE);
			int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
					TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
					vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				return 0;
			case 2:
				// new AboutBox().about("Controllo Uniformita'",
				// this.getClass());
				new AboutBox().about("Controllo Uniformita'", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				step = true;
			case 4:
				boolean verbose = true;
				boolean test = false;
				boolean autoCalled = true;
				ResultsTable rt = prepUnifor(path1, path2, autoArgs, autoCalled, step, verbose, test);
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);
				retry = false;
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	/**
	 * preparation routine for mainUnifor
	 *
	 * @param path1      first image
	 * @param path2      second image
	 * @param autoArgs   arguments passed from Sequenze_ or "0" in manual
	 * @param autoCalled true if called from Sequenze
	 * @param step       step-by-step demo and test mode
	 *
	 * @param verbose    additional informations on screen
	 * @param test       test mode
	 */
	public static ResultsTable prepUnifor(String path1, String path2, String autoArgs, boolean autoCalled, boolean step,
			boolean verbose, boolean test) {

		ImagePlus imp0 = UtilAyv.openImageNoDisplay(path1, verbose);
		int height = imp0.getHeight();
		int width = imp0.getWidth();
		int[] roiData = readPreferences(width, height);

		ResultsTable rt = mainUnifor(path1, path2, roiData, autoArgs, autoCalled, step, verbose, test);

		return rt;
	}

	/**
	 * main uniformity calculations
	 *
	 * @param path1      first image
	 * @param path2      second image
	 * @param roiData    geometric data for the ROI
	 * @param autoArgs   arguments passed from Sequenze_ or "0" in manual
	 * @param autoCalled true if called from Sequenze
	 * @param step       step-by-step demo and test mode *
	 * @param verbose    additional informations on screen
	 * @param test       test mode
	 * @return
	 */
	public static ResultsTable mainUnifor(String path1, String path2, int[] roiData, String autoArgs,
			boolean autoCalled, boolean step, boolean verbose, boolean test) {

		// step=true;
		// verbose=true;

		boolean accetta = false;
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);

		do {
			ImagePlus imp1 = null;
			ImagePlus imp2 = null;

			if (verbose) {
				imp1 = UtilAyv.openImageMaximized(path1);
				imp2 = UtilAyv.openImageMaximized(path2);
			} else {
				imp1 = UtilAyv.openImageNoDisplay(path1, true);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			}

			ImageUtils.backgroundEnhancement(0, 0, 10, imp1);

			int height = imp1.getHeight();
			int width = imp1.getWidth();

			int xRoi1 = roiData[0];
			int yRoi1 = roiData[1];
			int latoLungoRoi1 = roiData[2];
			int latoCortoRoi1 = roiData[3];
//			MyLog.waitHere("lungoRoi1= "+latoLungoRoi1+" cortoRoi1= "+latoCortoRoi1);

			imp1.setRoi(xRoi1, yRoi1, latoLungoRoi1, latoCortoRoi1);

			if (verbose) {
				imp1.getWindow().toFront();
			}

			if (!test) {
				msgMainRoiPositioning();
			}
			Rectangle boundingRectangle = imp1.getProcessor().getRoi();
			writeStoredRoiData(boundingRectangle);

			int latoLungoRoi2 = (boundingRectangle.width);
			int latoCortoRoi2 = (boundingRectangle.height);

			int xRoi2 = boundingRectangle.x + ((boundingRectangle.width - latoLungoRoi2) / 2);
			int yRoi2 = boundingRectangle.y + ((boundingRectangle.height - latoCortoRoi2) / 2);

			ImageStatistics stat1 = imp1.getStatistics();
			double mean1 = stat1.mean;
			if (!test) {
				msgRoiData(step, mean1);
			}

			double uiPerc1 = uiPercCalculation(stat1.max, stat1.min);

			int[] pixels1 = pixVectorize(imp1);

			double naad1 = naadCalculation(pixels1);

			ImagePlus impDiff = UtilAyv.genImaDifference(imp1, imp2);
			if (verbose) {
				UtilAyv.showImageMaximized(impDiff);
			}
			if (!test) {
				msgElabImaDiff(step);
			}
			impDiff.setRoi(xRoi2, yRoi2, latoLungoRoi2, latoCortoRoi2);

			ImageStatistics statImaDiff = impDiff.getStatistics();
			if (verbose) {
				impDiff.updateAndDraw();
			}

			double meanImaDiff = statImaDiff.mean;
			double stdDevImaDiff = statImaDiff.stdDev;
			if (!test) {
				msgImaDiffData(step, meanImaDiff);
			}
			double noiseImaDiff = stdDevImaDiff / Math.sqrt(2);
			double snRatio = Math.sqrt(2) * mean1 / stdDevImaDiff;
			if (!test) {
				msgSnRatio(step, uiPerc1, snRatio);
			}

			IJ.setMinAndMax(imp1, 10, 30);

			int xRoi9 = MyConst.P19_X_ROI_BACKGROUND - MyConst.P19_LATO_ROI_BACKGROUND / 2;
			int yRoi9 = MyConst.P19_Y_ROI_BACKGROUND - MyConst.P19_LATO_ROI_BACKGROUND / 2;
			if (test) {
				xRoi9 = xRoi9 - 40;
			}

			ImageStatistics statBkg = ImageUtils.backCalc(xRoi9, yRoi9, MyConst.P19_LATO_ROI_BACKGROUND, imp1, step,
					false, test);
			double meanBkg = statBkg.mean;
			String name1 = simpath + "\\";
			int[][] classiSimulata = generaSimulata(xRoi2, yRoi2, latoLungoRoi2, latoCortoRoi2, imp1, name1, step,
					verbose, test);

			// String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);
			TableCode tc1 = new TableCode();
			String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");

			// String[][] tabCodici = new InputOutput().readFile1(
			// MyConst.CODE_FILE, MyConst.TOKENS4);

			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp1, tabCodici, VERSION, autoCalled);

			// put values in ResultsTable

			// MyLog.logVector(info1, "info1");
			// MyLog.waitHere();

			String t1 = "TESTO";
			String s2 = "VALORE";
			String s3 = "roi_x";
			String s4 = "roi_y";
			String s5 = "roi_b";
			String s6 = "roi_h";

			rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);
			rt.showRowNumbers(true);

			rt.addValue(t1, "Segnale");
			rt.addValue(s2, mean1);
			rt.addValue(s3, stat1.roiX);
			rt.addValue(s4, stat1.roiY);
			rt.addValue(s5, stat1.roiWidth);
			rt.addValue(s6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addValue(t1, "Rumore");
			rt.addValue(s2, noiseImaDiff);
			rt.addValue(s3, stat1.roiX);
			rt.addValue(s4, stat1.roiY);
			rt.addValue(s5, stat1.roiWidth);
			rt.addValue(s6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addValue(t1, "SNR");
			rt.addValue(s2, snRatio);
			rt.addValue(s3, stat1.roiX);
			rt.addValue(s4, stat1.roiY);
			rt.addValue(s5, stat1.roiWidth);
			rt.addValue(s6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addValue(t1, "Ghost_1");
			rt.addValue(s2, 0);
			rt.addValue(s3, 0);
			rt.addValue(s4, 0);
			rt.addValue(s5, 0);
			rt.addValue(s6, 0);

			rt.incrementCounter();
			rt.addValue(t1, "Ghost_2");
			rt.addValue(s2, 0);
			rt.addValue(s3, 0);
			rt.addValue(s4, 0);
			rt.addValue(s5, 0);
			rt.addValue(s6, 0);

			rt.incrementCounter();
			rt.addValue(t1, "Ghost_3");
			rt.addValue(s2, 0);
			rt.addValue(s3, 0);
			rt.addValue(s4, 0);
			rt.addValue(s5, 0);
			rt.addValue(s6, 0);

			rt.incrementCounter();
			rt.addValue(t1, "Ghost_4");
			rt.addValue(s2, 0);
			rt.addValue(s3, 0);
			rt.addValue(s4, 0);
			rt.addValue(s5, 0);
			rt.addValue(s6, 0);

			rt.incrementCounter();
			rt.addValue(t1, "Unif.Integr.%");
			rt.addValue(s2, uiPerc1);
			rt.addValue(s3, stat1.roiX);
			rt.addValue(s4, stat1.roiY);
			rt.addValue(s5, stat1.roiWidth);
			rt.addValue(s6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addValue(t1, "NAAD");
			rt.addValue(s2, naad1);
			rt.addValue(s3, stat1.roiX);
			rt.addValue(s4, stat1.roiY);
			rt.addValue(s5, stat1.roiWidth);
			rt.addValue(s6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addValue(t1, "Bkg");
			rt.addValue(s2, statBkg.mean);
			rt.addValue(s3, statBkg.roiX);
			rt.addValue(s4, statBkg.roiY);
			rt.addValue(s5, statBkg.roiWidth);
			rt.addValue(s6, statBkg.roiHeight);

			rt.incrementCounter();
			rt.addValue(t1, "Pos");
			rt.addValue(s2, 0);

			String[] levelString = { "+20%", "+10%", "-10%", "-20%", "fondo" };

			for (int i1 = 0; i1 < classiSimulata.length; i1++) {
				rt.incrementCounter();
				rt.addValue(t1, ("Classe" + classiSimulata[i1][0]) + "_" + levelString[i1]);
				rt.addValue(s2, classiSimulata[i1][1]);
			}

			if (verbose && !test) {
				rt.show("Results");
			}

			if (autoCalled && !test) {
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

	/**
	 * Read preferences from IJ_Prefs.txt
	 *
	 * @param width  image width
	 * @param height image height
	 * @param limit  border limit for object placement on image
	 * @return
	 */
	public static int[] readPreferences(int width, int height) {

		int latoLungo = (int) MyConst.P19_LATO_LUNGO_ROI;
		int latoCorto = (int) MyConst.P19_LATO_CORTO_ROI;
//		MyLog.waitHere("lungo= "+latoLungo+" corto= "+latoCorto);
		int xRoi1 = ReadDicom.readInt(Prefs.get("prefer.P19rmnXRoi1", Integer.toString(height / 2 - latoLungo / 2)));
		int yRoi1 = ReadDicom.readInt(Prefs.get("prefer.P19rmnYRoi1", Integer.toString(width / 2 - latoCorto / 2)));
		int[] defaults = { xRoi1, yRoi1, latoLungo, latoCorto };
		return defaults;
	}

	/**
	 * Write preferences into IJ_Prefs.txt
	 *
	 * @param boundingRectangle
	 */
	public static void writeStoredRoiData(Rectangle boundingRectangle) {

		Prefs.set("prefer.P19rmnLatoLungoRoi", Integer.toString(boundingRectangle.width));
		Prefs.set("prefer.P19rmnLatoCortoRoi", Integer.toString(boundingRectangle.height));
		Prefs.set("prefer.P19rmnXRoi1", Integer.toString(boundingRectangle.x));
		Prefs.set("prefer.P19rmnYRoi1", Integer.toString(boundingRectangle.y));
	}

	/**
	 * Calculation of Integral Uniformity Percentual
	 *
	 * @param max max signal
	 * @param min min signal
	 * @return
	 */
	public static double uiPercCalculation(double max, double min) {
		// Ui% = ( 1 - ( signalMax - signalMin ) / ( signalMax +
		// signalMin )) * 100
		double uiPerc = (1 - (max - min) / (max + min)) * 100;
		return uiPerc;
	}

	/**
	 * Ghost percentual calculation
	 *
	 * @param mediaGhost1 mean signal of ghost roi
	 * @param meanBkg     mean signal of background roi
	 * @param meanImage   mean signal on image roi
	 * @return
	 */
	public static double ghostPercCalculation(double mediaGhost1, double meanBkg, double meanImage) {
		double ghostPerc = ((mediaGhost1 - meanBkg) / meanImage) * 100.0;
		return ghostPerc;
	}

	/**
	 * Test images extraction on a temporary directory, from test2.jar
	 *
	 * @return path of temporarary directory
	 */
	private String findTestImages() {
		InputOutput io = new InputOutput();
		io.extractFromJAR(MyConst.TEST_FILE, "HUSA_001testP3", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "HUSA_002testP3", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "HUSA2_01testP3", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "HUSA2_02testP3", "./Test2/");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY).getPath();
		return (home1);
	}

	/**
	 * Simulated 5 classes image
	 *
	 * @param xRoi    x roi coordinate
	 * @param yRoi    y roi coordinate
	 * @param diamRoi roi diameter
	 * @param imp     original image
	 * @param step    step-by-step mode
	 * @param test    autotest mode
	 * @return pixel counts of classes of the simulated image
	 */
	private static int[][] generaSimulata(int xRoi, int yRoi, int latoLungoRoi, int latoCortoRoi, ImagePlus imp,
			String filename, boolean step, boolean verbose, boolean test) {

		int xRoiSimulata = xRoi + (latoLungoRoi - MyConst.P3_DIAM_FOR_450_PIXELS) / 2;
		int yRoiSimulata = yRoi + (latoCortoRoi - MyConst.P3_DIAM_FOR_450_PIXELS) / 2;
		ImagePlus impSimulata = simulata5Classi(xRoiSimulata, yRoiSimulata, MyConst.P3_DIAM_FOR_450_PIXELS, imp);
		if (verbose) {
			UtilAyv.showImageMaximized(impSimulata);
			ImageUtils.backgroundEnhancement(0, 0, 10, impSimulata);
		}
		// UtilAyv.autoAdjust(impSimulata, impSimulata.getProcessor());
		impSimulata.updateAndDraw();
		msgImaSimulata(step);
		int[][] classiSimulata = numeroPixelsClassi(impSimulata);
		String patName = ReadDicom.readDicomParameter(imp, MyConst.DICOM_PATIENT_NAME);

		String codice1 = ReadDicom.readDicomParameter(imp, MyConst.DICOM_SERIES_DESCRIPTION);

		String codice = UtilAyv.getFiveLetters(codice1);

		String simName = filename + patName + codice + "sim.zip";

		if (!test) {
			impSimulata.setTitle(patName + codice + "sim");
			new FileSaver(impSimulata).saveAsZip(simName);
		}
		return classiSimulata;

	}

	/**
	 *
	 * @param imp1
	 * @return
	 */
	public static int[][] numeroPixelsClassi(ImagePlus imp1) {

		if (imp1 == null) {
			IJ.error("numeroPixelClassi ricevuto null");
			return (null);
		}
		int width = imp1.getWidth();
		ImageProcessor ip1 = imp1.getProcessor();
		short[] pixels1 = (short[]) ip1.getPixels();

		int[][] vetClassi = { { MyConst.LEVEL_5, 0 }, { MyConst.LEVEL_4, 0 }, { MyConst.LEVEL_3, 0 },
				{ MyConst.LEVEL_2, 0 }, { MyConst.LEVEL_1, 0 } };
		int offset = 0;
		int pix1 = 0;
		for (int y1 = 0; y1 < width; y1++) {
			for (int x1 = 0; x1 < (width); x1++) {
				offset = y1 * width + x1;
				pix1 = pixels1[offset];
				for (int i1 = 0; i1 < vetClassi.length; i1++) {
					if (pix1 == vetClassi[i1][0]) {
						vetClassi[i1][1] = vetClassi[i1][1] + 1;
						break;
					}
				}
			}
		}
		return (vetClassi);

	}

	/**
	 *
	 * @param sqX
	 * @param sqY
	 * @param sqR
	 * @param imp1
	 * @return
	 */
	public static ImagePlus simulata5Classi(int sqX, int sqY, int sqR, ImagePlus imp1) {

		if (imp1 == null) {
			IJ.error("Simula5Classi ricevuto null");
			return (null);
		}
		int width = imp1.getWidth();
		short[] pixels1 = UtilAyv.truePixels(imp1);

		imp1.setRoi(sqX, sqY, sqR, sqR);
		ImageStatistics stat1 = imp1.getStatistics();

		double mean = stat1.mean;
		double minus20 = mean * MyConst.MINUS_20_PERC;
		double minus10 = mean * MyConst.MINUS_10_PERC;
		double plus10 = mean * MyConst.PLUS_10_PERC;
		double plus20 = mean * MyConst.PLUS_20_PERC;
		// genero una immagine nera
		ImagePlus impSimulata = NewImage.createShortImage("Simulata", width, width, 1, NewImage.FILL_BLACK);
		ShortProcessor processorSimulata = (ShortProcessor) impSimulata.getProcessor();
		short[] pixelsSimulata = (short[]) processorSimulata.getPixels();

		short pixSorgente = 0;
		short pixSimulata = 0;
		int posizioneArrayImmagine = 0;

		for (int y = 0; y < width; y++) {
			for (int x = 0; x < width; x++) {
				posizioneArrayImmagine = y * width + x;
				pixSorgente = pixels1[posizioneArrayImmagine];
				if (pixSorgente > plus20) {
					pixSimulata = MyConst.LEVEL_5;
				} else if (pixSorgente > plus10) {
					pixSimulata = MyConst.LEVEL_4;
				} else if (pixSorgente > minus10) {
					pixSimulata = MyConst.LEVEL_3;
				} else if (pixSorgente > minus20) {
					pixSimulata = MyConst.LEVEL_2;
				} else {
					pixSimulata = MyConst.LEVEL_1;
				}
				pixelsSimulata[posizioneArrayImmagine] = pixSimulata;
			}
		}
		processorSimulata.resetMinAndMax();
		return impSimulata;
	}

	/**
	 * Siemens test image expected results
	 *
	 * @return
	 */
	double[] referenceSiemens() {
		double mean = 1679.260738831615;
		double noise = 5.048954649732993;
		double snRatio = 332.5957263094887;
		double g5 = -0.3480660610768231;
		double g6 = -0.1697929188035829;
		double g7 = -0.3311056141375085;
		double g8 = -0.08329463941307846;
		double uiPerc = 89.70727101038716;
		double bkg = 11.401898734177216;
		double c4 = 0;
		double c3 = 0;
		double c2 = 22579;
		double c1 = 358;
		double c0 = 42599;
		double[] vetReference = { mean, noise, snRatio, g5, g6, g7, g8, uiPerc, bkg, c4, c3, c2, c1, c0 };
		return vetReference;
	}

	/**
	 * Ge test image expected results
	 *
	 * @return
	 */
	double[] referenceGe() {
		double mean = 1546.827426975945;
		double noise = 14.489602167104621;
		double snRatio = 106.75430623538224;
		double g5 = -0.1728732362949611;
		double g6 = -0.016571280638925212;
		double g7 = -0.23138417781017862;
		double g8 = 0.03211964271989232;
		double uiPerc = 89.54559898315857;
		double bkg = 16.79746835443038;
		double c4 = 22;
		double c3 = 1136;
		double c2 = 22000;
		double c1 = 99;
		double c0 = 42279;
		double[] vetReference = { mean, noise, snRatio, g5, g6, g7, g8, uiPerc, bkg, c4, c3, c2, c1, c0 };
		return vetReference;
	}

	/**
	 * Self test execution menu
	 */
	public void selfTestMenu() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			switch (userSelection2) {
			case 1: {
				// GE
				String home1 = findTestImages();
				String path1 = home1 + "/HUSA2_01testP3";
				String path2 = home1 + "/HUSA2_02testP3";
				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;
				double[] vetReference = referenceGe();
				int[] roiData = { MyConst.P3_X_ROI_TESTGE, MyConst.P3_Y_ROI_TESTGE, MyConst.P3_DIAM_PHANTOM };
				ResultsTable rt1 = mainUnifor(path1, path2, roiData, autoArgs, autoCalled, step, verbose, test);
				double[] vetResults = UtilAyv.vectorizeResults(rt1);

				boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P3_vetName);
				if (ok) {
					MyMsg.msgTestPassed();
				} else {
					MyMsg.msgTestFault();
				}
				UtilAyv.afterWork();
				break;
			}
			case 2:
				// Siemens
				String home1 = findTestImages();
				String path1 = home1 + "/HUSA_001testP3";
				String path2 = home1 + "/HUSA_002testP3";
				double[] vetReference = referenceSiemens();
				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;
				int[] roiData = { MyConst.P3_X_ROI_TESTSIEMENS, MyConst.P3_Y_ROI_TESTSIEMENS, MyConst.P3_DIAM_PHANTOM };
				ResultsTable rt1 = mainUnifor(path1, path2, roiData, autoArgs, autoCalled, step, verbose, test);
				double[] vetResults = UtilAyv.vectorizeResults(rt1);
				boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P3_vetName);
				if (ok) {
					MyMsg.msgTestPassed();
				} else {
					MyMsg.msgTestFault();
				}
				UtilAyv.afterWork();
				break;
			}
		} else {
			UtilAyv.noTest2();
		}
		return;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {
		// if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
		String home1 = findTestImages();
		String path1 = home1 + "HUSA_001testP3";
		String path2 = home1 + "HUSA_002testP3";
		double[] vetReference = referenceSiemens();

		String autoArgs = "-1";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;
		int[] roiData = { MyConst.P3_X_ROI_TESTSIEMENS, MyConst.P3_Y_ROI_TESTSIEMENS, MyConst.P3_DIAM_PHANTOM };
		ResultsTable rt1 = mainUnifor(path1, path2, roiData, autoArgs, autoCalled, step, verbose, test);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P3_vetName);
		if (ok) {
			IJ.log("Il test di p3rmn_ UNIFORMITA' CIRCOLARE e' stato SUPERATO");
		} else {
			IJ.log("Il test di p3rmn_ UNIFORMITA' CIRCOLARE evidenzia degli ERRORI");
		}
		return;
		// } else {
		// IJ.log("Non trovato il file Test2.jar indispensabile per il test");
		// }
	}

	private static void msgMainRoiPositioning() {
		ButtonMessages.ModelessMsg("Posizionare ROI per calcolo UNIFORMITA' premere CONTINUA", "CONTINUA");
	}

	private static void msgRoiData(boolean step, double mean1) {
		if (step) {
			ButtonMessages.ModelessMsg("media roi=" + mean1, "CONTINUA");
		}
	}

	private static void msgElabImaDiff(boolean step) {
		if (step) {
			ButtonMessages.ModelessMsg(
					"Elaborata immagine differenza                                                                                        <11>",
					"CONTINUA");
		}
	}

	private static void msgImaDiffData(boolean step, double meanImaDiff) {
		if (step) {
			ButtonMessages.ModelessMsg(" mediaImaDiff=" + meanImaDiff + "  ", "CONTINUA", "CHIUDI");
		}
	}

	private static void msgImaSimulata(boolean step) {
		if (step) {
			ButtonMessages.ModelessMsg("Immagine Simulata", "CONTINUA");
		}
	}

	private static void msgSnRatio(boolean step, double uiPerc1, double snRatio) {
		if (step) {
			ButtonMessages.ModelessMsg("Uniformita' integrale=" + uiPerc1 + "  Rapporto segnale/rumore sn2=" + snRatio,
					"CONTINUA");
		}
	}

	/**
	 * 13/11/2016 Nuovo algoritmo per uniformita' per immagini con grappa (prosit!)
	 * datomi da Lorella CHIAMASI NAAD
	 *
	 * @param pixListSignal
	 * @return
	 */

	public static double naadCalculation(int[] pixListSignal) {

		double mean1 = ArrayUtils.vetMean(pixListSignal);
		// MyLog.waitHere("mean1= "+mean1);
		double val = 0;
		double sum1 = 0;
		for (int element : pixListSignal) {
			val = Math.abs(element - mean1);
			sum1 = sum1 + val;
		}
		// MyLog.waitHere("sum1= "+sum1);
		double result = sum1 / (mean1 * pixListSignal.length);
		return result;
	}

//	public static int[] pixVectorize(ImagePlus imp1) {
//
//		if (imp1 == null)
//			MyLog.waitHere("imp1==null");
//		Roi roi1 = imp1.getRoi();
//		MyLog.waitHere();
//		ArrayList<Integer> pixList1 = new ArrayList<Integer>();
//		ImageProcessor ip1 = imp1.getProcessor();
//		if (ip1 == null)
//			MyLog.waitHere("ip1==null");
//		ImageProcessor mask1 = roi1 != null ? roi1.getMask() : null;
//		Rectangle r1 = roi1 != null ? roi1.getBounds() : new Rectangle(0, 0, ip1.getWidth(), ip1.getHeight());
//		for (int y = 0; y < r1.height; y++) {
//			for (int x = 0; x < r1.width; x++) {
//				if (mask1 == null || mask1.getPixel(x, y) != 0) {
//					pixList1.add((int) ip1.getPixelValue(x + r1.x, y + r1.y));
//					if (debug)
//						ip1.putPixel(x, y, 10000);
//				}
//			}
//		}
//		int[] pixels = new int[pixList1.size()];
//		int i1 = 0;
//		for (Integer n : pixList1) {
//			pixels[i1++] = n;
//		}
//		return pixels;
//	}

	public static int[] pixVectorize(ImagePlus imp1) {

		ArrayList<Integer> pixList1 = new ArrayList<Integer>();
		Calibration cal1 = imp1.getCalibration();

		Roi roi1 = imp1.getRoi();
		if (roi1 != null && !roi1.isArea()) {
			roi1 = null;
		}
		ImageProcessor ip1 = imp1.getProcessor();
		ImageProcessor mask1 = roi1 != null ? roi1.getMask() : null;
		Rectangle r1 = roi1 != null ? roi1.getBounds() : new Rectangle(0, 0, ip1.getWidth(), ip1.getHeight());
		int aux1 = 0;
		for (int y1 = 0; y1 < r1.height; y1++) {
			for (int x1 = 0; x1 < r1.width; x1++) {
				if (mask1 == null || mask1.getPixel(x1, y1) != 0) {
					aux1 = (int) ip1.getPixelValue(x1 + r1.x, y1 + r1.y);
					pixList1.add(aux1);
//					if (debug)
//					ip1.putPixel(x1+r1.x,y1+r1.y, 10000);
				}
			}
		}
		int[] pixels = new int[pixList1.size()];
		int i1 = 0;
		for (Integer n : pixList1) {
			pixels[i1++] = n;
		}
		return pixels;
	}

}
