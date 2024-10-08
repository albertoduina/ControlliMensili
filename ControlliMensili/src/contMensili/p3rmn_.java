package contMensili;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.StringTokenizer;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.NewImage;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.io.FileSaver;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import utils.AboutBox;
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
 * Analizza UNIFORMITA', SNR, GHOSTS.
 *
 *
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 *
 */

public class p3rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "UNIFORMITA' SNR GHOSTS per bobine circolari manuale";

	private static String TYPE = " >> CONTROLLO UNIFORMITA'_____________";

	private static String fileDir = "";
	private static boolean debug = true;
	private static boolean mylogger = true;

	@Override
	public void run(String args) {

		UtilAyv.setMyPrecision();

		// -----------------------------
		if (IJ.versionLessThan("1.43k")) {
			return;
		}
		// -----------------------------
		Count c1 = new Count();
		if (!c1.jarCount("iw2ayv_")) {
			return;
		}
		// -----------------------------
		String className = this.getClass().getName();
		String user1 = System.getProperty("user.name");
		// -----------------------------
		TableCode tc1 = new TableCode();
		String iw2ayv1 = tc1.nameTable("codici", "csv");
		TableExpand tc2 = new TableExpand();
		String iw2ayv2 = tc1.nameTable("expand", "csv");
		// -----------------------------
		VERSION = user1 + ":" + className + "build_" + MyVersion.getVersion() + ":iw2ayv_build_"
				+ MyVersionUtils.getVersion() + ":" + iw2ayv1 + ":" + iw2ayv2;
		// -----------------------------
		fileDir = Prefs.get("prefer.string1", "none");
		// -----------------------------
		if (mylogger) {
			MyFileLogger.logger.info("p3rmn_>>> fileDir= " + fileDir);
		}
		// -----------------------------
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
		MyLog.appendLog(fileDir + "MyLog.txt", "p3 riceve " + autoArgs);

		// the autoArgs are passed from Sequenze_
		// possibilities:
		// 1 token -1 = silentAutoTest
		// 2 tokens auto
		// 4 tokens auto
		//

		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);

		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p3rmn_");
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
		if (nTokens == MyConst.TOKENS2) {
			// UtilAyv.checkImages(vetRiga, iw2ayvTable, 2, debug);
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			UtilAyv.checkImages2(path1, path2, debug);

			MyLog.logDebug(vetRiga[0], "P3", fileDir);
			MyLog.logDebug(vetRiga[1], "P3", fileDir);

		} else {
			// UtilAyv.checkImages(vetRiga, iw2ayvTable, 3, debug);
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
			UtilAyv.checkImages2(path1, path2, debug);

			MyLog.logDebug(vetRiga[0], "P3", fileDir);
			MyLog.logDebug(vetRiga[2], "P3", fileDir);
		}

		boolean retry = false;
		boolean step = false;
		ResultsTable rt = null;
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
				rt = prepUnifor(path1, path2, autoArgs, autoCalled, step, verbose, test);
				if (rt == null) {
					ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, verbose);
					TableCode tc1 = new TableCode();
					String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");
					String[] info11 = ReportStandardInfo.getSimpleStandardInfo(path1, imp11, tabCodici, VERSION, autoCalled);
					rt = ReportStandardInfo.abortResultTable_P3(info11);
				}
				rt.showRowNumbers(true);
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
		int[] roiData = readPreferences(width, height, MyConst.P3_ROI_LIMIT);

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
	@SuppressWarnings("deprecation")
	public static ResultsTable mainUnifor(String path1, String path2, int[] roiData, String autoArgs,
			boolean autoCalled, boolean step, boolean verbose, boolean test) {

		boolean accetta = false;
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		Overlay over1 = new Overlay();
		Overlay over2 = new Overlay();
		Overlay over3 = new Overlay();

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
			ImagePlus impDiff = UtilAyv.genImaDifference(imp1, imp2);
			imp1.setOverlay(over1);
			imp2.setOverlay(over2);
			impDiff.setOverlay(over3);

			// (ImagePlus imp1, int xroi, int yroi, int droi, Overlay over, Color color)

			int height = imp1.getHeight();
			int width = imp1.getWidth();
			// calcolo i dati iniziali ROI fantoccio da quelli memorizzati dell'ultima
			// misura
			int xRoi1 = roiData[0];
			int yRoi1 = roiData[1];
			int diamRoi1 = roiData[2];
			mySetRoi(imp1, xRoi1, yRoi1, diamRoi1, null, Color.green);
			mySetRoi(imp2, xRoi1, yRoi1, diamRoi1, null, Color.green);
			mySetRoi(impDiff, xRoi1, yRoi1, diamRoi1, null, Color.green);

			if (verbose) {
				UtilAyv.showImageMaximized(impDiff);
				imp1.getWindow().toFront();
			}

			if (!test) {
				msgElabImaDiff(step);
			}
			int resp = 0;

			if (!test) {
				resp = ButtonMessages.ModelessMsg(
						"Posizionare ROI diamFantoccio e premere CONTINUA,  altrimenti, se l'immagine NON E'ACCETTABILE premere ANNULLA per passare alle successive",
						"CONTINUA", "ANNULLA");
			}

			if (resp == 1) {
				return null;
			}

			// leggo dove hanno posizionato la ROI fantoccio sull'immagine attiva, questi
			// sono i dati definitivi
			ImagePlus impActive1 = WindowManager.getCurrentImage();
			Rectangle boundingRectangle = impActive1.getRoi().getBounds();
			writeStoredRoiData(boundingRectangle);
			int diamRoi1x = (boundingRectangle.width);
			int xRoi1x = boundingRectangle.x + ((boundingRectangle.width - diamRoi1x) / 2);
			int yRoi1x = boundingRectangle.y + ((boundingRectangle.height - diamRoi1x) / 2);
			// disegno la Roi definitiva fantoccio sui 3 overlay delle immagini
			mySetRoi(imp1, xRoi1x, yRoi1x, diamRoi1x, over1, Color.green);
			mySetRoi(imp2, xRoi1x, yRoi1x, diamRoi1x, over2, Color.green);
			mySetRoi(impDiff, xRoi1x, yRoi1x, diamRoi1x, over3, Color.green);
			imp1.deleteRoi();
			imp2.deleteRoi();
			impDiff.deleteRoi();
			// calcolo i dati iniziali per la ROI 80%
			int diamRoi2 = (int) (boundingRectangle.width * MyConst.P3_AREA_PERC_80_DIAM);
			int xRoi2 = boundingRectangle.x + ((boundingRectangle.width - diamRoi2) / 2);
			int yRoi2 = boundingRectangle.y + ((boundingRectangle.height - diamRoi2) / 2);
			mySetRoi(imp1, xRoi2, yRoi2, diamRoi2, null, Color.red);
			mySetRoi(imp2, xRoi2, yRoi2, diamRoi2, null, Color.red);
			mySetRoi(impDiff, xRoi2, yRoi2, diamRoi2, null, Color.red);

			// IJ.log("roi 80% prima del riposizionamento: xRoi2= "+xRoi2+"
			// yRoi2= "+yRoi2+" diamRoi2= "+diamRoi2);

			if (!test) {
				msgRoi85percPositioning();
			}

			// leggo dove hanno posizionato la ROI 80% sull'immagine attiva, questi
			// sono i dati definitivi
			impActive1 = WindowManager.getCurrentImage();
			Rectangle boundingRectangle2 = impActive1.getProcessor().getRoi();
			diamRoi2 = boundingRectangle2.width;
			xRoi2 = boundingRectangle2.x + ((boundingRectangle2.width - diamRoi2) / 2);
			yRoi2 = boundingRectangle2.y + ((boundingRectangle2.height - diamRoi2) / 2);
			// disegno la Roi definitiva 80% sui 3 overlay delle immagini
			mySetRoi(imp1, xRoi2, yRoi2, diamRoi2, over1, Color.red);
			mySetRoi(imp2, xRoi2, yRoi2, diamRoi2, over2, Color.red);
			mySetRoi(impDiff, xRoi2, yRoi2, diamRoi2, over3, Color.red);
			imp1.deleteRoi();
			imp2.deleteRoi();
			impDiff.deleteRoi();
			// IJ.log("roi 80% dopo il riposizionamento: xRoi2= "+xRoi2+" yRoi2=
			// "+yRoi2+" diamRoi2= "+diamRoi2);

			ImageStatistics stat1 = imp1.getStatistics();
			double mean1 = stat1.mean;
			if (!test) {
				msg85percData(step, mean1);
			}

			double uiPerc1 = uiPercCalculation(stat1.max, stat1.min);
			double slicePosition = ReadDicom
					.readDouble(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_LOCATION));

//			ImagePlus impDiff = UtilAyv.genImaDifference(imp1, imp2);
//			if (verbose)
//				UtilAyv.showImageMaximized(impDiff);
//			if (!test)
//				msgElabImaDiff(step);
//			impDiff.setRoi(new OvalRoi(xRoi2, yRoi2, diamRoi2, diamRoi2));

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

			int xRoi5 = 1;
			int yRoi5 = height / 2 - MyConst.P3_DIAM_ROI_GHOSTS / 2;

			// TODO da provare
			IJ.setMinAndMax(imp1, 10, 30);

			// if (verbose)
			// UtilAyv.autoAdjust(imp1, imp1.getProcessor());
			ImageStatistics statGh1 = ghostRoi(xRoi5, yRoi5, imp1, 1, step, test);
			over1.addElement(imp1.getRoi());

			double mediaGhost1 = statGh1.mean;

			int xRoi6 = height / 2 - MyConst.P3_DIAM_ROI_GHOSTS / 2;
			int yRoi6 = 1;
			ImageStatistics statGh2 = ghostRoi(xRoi6, yRoi6, imp1, 2, step, test);
			over1.addElement(imp1.getRoi());

			double mediaGhost2 = statGh2.mean;

			int xRoi7 = height - MyConst.P3_DIAM_ROI_GHOSTS - 1;
			int yRoi7 = height / 2 - MyConst.P3_DIAM_ROI_GHOSTS / 2;
			ImageStatistics statGh3 = ghostRoi(xRoi7, yRoi7, imp1, 3, step, test);
			over1.addElement(imp1.getRoi());

			double mediaGhost3 = statGh3.mean;

			int xRoi8 = height / 2 - MyConst.P3_DIAM_ROI_GHOSTS / 2;
			int yRoi8 = height - MyConst.P3_DIAM_ROI_GHOSTS - 1;
			ImageStatistics statGh4 = ghostRoi(xRoi8, yRoi8, imp1, 4, step, test);
			over1.addElement(imp1.getRoi());

			double mediaGhost4 = statGh4.mean;

			int xRoi9 = height - MyConst.P3_DIAM_ROI_BACKGROUND - 1;
			int yRoi9 = width - MyConst.P3_DIAM_ROI_BACKGROUND - 1;
			if (test) {
				xRoi9 = xRoi9 - 40;
			}

			// @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@

			ImageStatistics statBkg = ImageUtils.backCalc(xRoi9, yRoi9, MyConst.P3_DIAM_ROI_BACKGROUND, imp1, step,
					true, test);
			double meanBkg = statBkg.mean;
			over1.addElement(imp1.getRoi());

			double ghostPerc1 = ghostPercCalculation(mediaGhost1, meanBkg, mean1);

			double ghostPerc2 = ghostPercCalculation(mediaGhost2, meanBkg, mean1);
			double ghostPerc3 = ghostPercCalculation(mediaGhost3, meanBkg, mean1);
			double ghostPerc4 = ghostPercCalculation(mediaGhost4, meanBkg, mean1);

			int[][] classiSimulata = generaSimulata(xRoi2, yRoi2, diamRoi2, imp1, fileDir, step, verbose, test);

			// String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);
			TableCode tc1 = new TableCode();
			String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");

			// String[][] tabCodici = new InputOutput().readFile1(
			// MyConst.CODE_FILE, MyConst.TOKENS4);

			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp1, tabCodici, VERSION, autoCalled);

			// put values in ResultsTable

			// MyLog.logVector(info1, "info1");
			// MyLog.waitHere();

			rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);
			rt.showRowNumbers(true);

			String t1 = "TESTO";
			String s2 = "VALORE";
			String s3 = "roi_x";
			String s4 = "roi_y";
			String s5 = "roi_b";
			String s6 = "roi_h";

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
			rt.addValue(s2, ghostPerc1);
			rt.addValue(s3, statGh1.roiX);
			rt.addValue(s4, statGh1.roiY);
			rt.addValue(s5, statGh1.roiWidth);
			rt.addValue(s6, statGh1.roiHeight);

			rt.incrementCounter();
			rt.addValue(t1, "Ghost_2");
			rt.addValue(s2, ghostPerc2);
			rt.addValue(s3, statGh2.roiX);
			rt.addValue(s4, statGh2.roiY);
			rt.addValue(s5, statGh2.roiWidth);
			rt.addValue(s6, statGh2.roiHeight);

			rt.incrementCounter();
			rt.addValue(t1, "Ghost_3");
			rt.addValue(s2, ghostPerc3);
			rt.addValue(s3, statGh3.roiX);
			rt.addValue(s4, statGh3.roiY);
			rt.addValue(s5, statGh3.roiWidth);
			rt.addValue(s6, statGh3.roiHeight);

			rt.incrementCounter();
			rt.addValue(t1, "Ghost_4");
			rt.addValue(s2, ghostPerc4);
			rt.addValue(s3, statGh4.roiX);
			rt.addValue(s4, statGh4.roiY);
			rt.addValue(s5, statGh4.roiWidth);
			rt.addValue(s6, statGh4.roiHeight);

			rt.incrementCounter();
			rt.addValue(t1, "Unif.Integr.%");
			rt.addValue(s2, uiPerc1);
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
			rt.addValue(s2, slicePosition);

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
	public static int[] readPreferences(int width, int height, int limit) {

		int diam = ReadDicom.readInt(Prefs.get("prefer.p3rmnDiamFantoc", Integer.toString(width * 2 / 3)));
		int xRoi1 = ReadDicom.readInt(Prefs.get("prefer.p3rmnXRoi1", Integer.toString(height / 2 - diam / 2)));
		int yRoi1 = ReadDicom.readInt(Prefs.get("prefer.p3rmnYRoi1", Integer.toString(width / 2 - diam / 2)));
		if (diam < limit) {
			diam = height * 2 / 3;
		}
		if (xRoi1 < limit) {
			xRoi1 = height / 2 - diam / 2;
		}
		if (yRoi1 < limit) {
			yRoi1 = width / 2 - diam / 2;
		}
		int[] defaults = { xRoi1, yRoi1, diam };
		return defaults;
	}

	/**
	 * Write preferences into IJ_Prefs.txt
	 *
	 * @param boundingRectangle
	 */
	public static void writeStoredRoiData(Rectangle boundingRectangle) {

		Prefs.set("prefer.p3rmnDiamFantoc", Integer.toString(boundingRectangle.width));
		Prefs.set("prefer.p3rmnXRoi1", Integer.toString(boundingRectangle.x));
		Prefs.set("prefer.p3rmnYRoi1", Integer.toString(boundingRectangle.y));
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
	private static int[][] generaSimulata(int xRoi, int yRoi, int diamRoi, ImagePlus imp, String filename, boolean step,
			boolean verbose, boolean test) {

		int xRoiSimulata = xRoi + (diamRoi - MyConst.P3_DIAM_FOR_450_PIXELS) / 2;
		int yRoiSimulata = yRoi + (diamRoi - MyConst.P3_DIAM_FOR_450_PIXELS) / 2;
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

		// if (codice1.length() >= 4) {
		// codice = ReadDicom
		// .readDicomParameter(imp, MyConst.DICOM_SERIES_DESCRIPTION)
		// .substring(0, 4).trim();
		// } else {
		// codice = "____";
		// }

		String simName = filename + patName + codice + "sim.zip";

		if (!test) {
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

		imp1.setRoi(new OvalRoi(sqX, sqY, sqR, sqR));
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
	 * Ghost roi creation and calculation
	 *
	 * @param xRoi  x roi coordinate
	 * @param yRoi  y roi coordinate
	 * @param imp   image
	 * @param count ghost number (for the message)
	 * @param step  step mode
	 * @return statistics
	 */
	private static ImageStatistics ghostRoi(int xRoi, int yRoi, ImagePlus imp, int count, boolean step, boolean test) {

		ImageStatistics stat = null;
		boolean redo = true;
		do {
			imp.setRoi(new OvalRoi(xRoi, yRoi, MyConst.P3_DIAM_ROI_GHOSTS, MyConst.P3_DIAM_ROI_GHOSTS));
			imp.getRoi().setStrokeWidth(1.1);

			if (imp.isVisible()) {
				imp.getWindow().toFront();
			}
			if (!test) {
				msgGhostRoi(count);
			}
			stat = imp.getStatistics();
			if (stat.mean == 0) {
				redo = true;
			} else {
				redo = false;
			}
			if (redo) {
				msgMoveGhostRoi();
			}
			msgSignalGhostRoi(step, stat.mean);
		} while (redo);
		return stat;
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

	public static void mySetRoi(ImagePlus imp1, int xroi, int yroi, int droi, Overlay over, Color color) {

		imp1.setRoi(new OvalRoi(xroi, yroi, droi, droi));
		if (imp1.isVisible()) {
			imp1.getWindow().toFront();
		}
		imp1.getRoi().setStrokeWidth(1.1);

		if (color != null) {
			imp1.getRoi().setStrokeColor(color);
		}
		if (over != null) {
			over.addElement(imp1.getRoi());
		}

	}

//	private static void msgMainRoiPositioning() {
//		ButtonMessages.ModelessMsg("Posizionare ROI diamFantoccio e premere CONTINUA", "CONTINUA");
//	}

	private static void msgRoi85percPositioning() {
		ButtonMessages.ModelessMsg("Puoi modificare la posizione ROI con area 80%", "CONTINUA");
	}

	private static void msg85percData(boolean step, double mean1) {
		if (step) {
			ButtonMessages.ModelessMsg("media roi 85%=" + mean1, "CONTINUA");
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

	private static void msgGhostRoi(int count) {
		ButtonMessages.ModelessMsg("Posiziona  la ROI ghost" + count + " (ctrl+shift+E=ridisegna)", "CONTINUA");
	}

	public static void msgSignalGhostRoi(boolean step, double mean) {
		if (step) {
			ButtonMessages.ModelessMsg("Segnale medio =" + mean, "CONTINUA");
		}
	}

	private static void msgMoveGhostRoi() {
		ButtonMessages.ModalMsg("ATTENZIONE la posizione scelta per il ghost da' segnale medio =0 SPOSTARLO",
				"CONTINUA");
	}

}
