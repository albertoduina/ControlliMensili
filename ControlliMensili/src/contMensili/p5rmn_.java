package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;

import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;

import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import ij.util.Tools;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.InputOutput;
import utils.MyMsg;
import utils.MyConst;
import utils.MyFileLogger;
import utils.MyLog;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableSequence;
import utils.UtilAyv;

import utils.CustomCanvasGeneric;
import utils.ImageUtils;

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
 * Analizza UNIFORMITA', SNR, FWHM per le bobine superficiali
 * 
 * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
 * directory plugins
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */
public class p5rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "UNIFORMITA', SNR, FWHM per bobine superficiali manuale";

	private String TYPE = " >> CONTROLLO SUPERFICIALI____________";

	private static String fileDir = "";

	// private static String simulataName = "";

	// private boolean profiVert = false;

	public void run(String args) {
		UtilAyv.setMyPrecision();


		//
		// nota bene: le seguenti istruzioni devono essere all'inizio, in questo
		// modo il messaggio viene emesso, altrimenti si ha una eccezione
		//

		try {
			Class.forName("utils.IW2AYV");
		} catch (ClassNotFoundException e) {
			IJ.error("ATTENZIONE, manca il file iw2ayv_xxx.jar");
			return;
		}

		if (IJ.versionLessThan("1.43k"))
			return;

		fileDir = Prefs.get("prefer.string1", "none");

		MyFileLogger.logger.info("p5rmn_>>> fileDir = " + fileDir);
		MyFileLogger.logger.info("p5rmn_argomenti ricevuti: >" + args + "<");

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}
		return;
	}

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
				// new AboutBox().about("Controllo Bobine Superficiali",
				// this.getClass());
				new AboutBox().about("Controllo Bobine Superficiali",
						MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				selfTestMenu();
				retry = true;
				break;
			case 4:
				step = true;
			case 5:
				String[] path = new String[3];
				path[0] = UtilAyv
						.imageSelection("SELEZIONARE PRIMA ACQUISIZIONE PRIMO ECO...");
				if (path[0] == null)
					return 0;

				path[1] = UtilAyv
						.imageSelection("SELEZIONARE SECONDA ACQUISIZIONE PRIMO ECO...");
				if (path[1] == null)
					return 0;

				path[2] = UtilAyv
						.imageSelection("SELEZIONARE PRIMA  ACQUISIZIONE SECONDO ECO...");
				if (path[2] == null)
					return 0;
				boolean verticalProfile = true;
				boolean autoCalled = false;
				boolean verbose = false;
				boolean test = false;
				int sqX = 0;
				int sqY = 0;
				// ResultsTable rt1 = mainUnifor(path, sqX, sqY, "0",
				// verticalProfile, autoCalled, step, verbose, test);
				ResultsTable rt1 = mainUnifor(path, sqX, sqY, verticalProfile,
						autoCalled, step, verbose, test);
				if (rt1 == null)
					return 0;

				rt1.show("Results");

				retry = true;
				UtilAyv.afterWork();
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p5 riceve " + autoArgs);

		// IJ.log("p5rmn_.autoMenu autoargs= " + autoArgs);
		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p5rmn_");
			selfTestSilent();
			return 0;
		}

		if ((nTokens != MyConst.TOKENS2) && (nTokens != MyConst.TOKENS4)) {
			MyMsg.msgParamError();
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);

		String[] path = new String[3];
		if (nTokens == MyConst.TOKENS2) {
			path[0] = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path[1] = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			MyLog.logDebug(vetRiga[0], "P5", fileDir);
			MyLog.logDebug(vetRiga[1], "P5", fileDir);
			// path3 = lr.getPath(strRiga3, riga2);
		} else {
			path[0] = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path[1] = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
			path[2] = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			MyLog.logDebug(vetRiga[0], "P5", fileDir);
			MyLog.logDebug(vetRiga[1], "P5", fileDir);
			MyLog.logDebug(vetRiga[2], "P5", fileDir);
		}

		boolean direz = decodeDirez(TableSequence.getDirez(iw2ayvTable,
				vetRiga[0]));

		boolean step = false;
		boolean retry = false;
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
				// new AboutBox().about("Controllo Bobine Superficiali",
				// this.getClass());
				new AboutBox().about("Controllo Bobine Superficiali",
						MyVersion.CURRENT_VERSION);

				retry = true;
				break;
			case 3:
				step = true;
				// retry = false;
				// break;
			case 4:
				// step = false;
				retry = false;
				boolean verticalProfile = direz;
				boolean autoCalled = true;
				boolean verbose = true;
				boolean test = false;
				int sqX = 0;
				int sqY = 0;
				// ResultsTable rt1 = mainUnifor(path, sqX, sqY, autoArgs,
				// verticalProfile, autoCalled, step, verbose, test);
				ResultsTable rt1 = mainUnifor(path, sqX, sqY, verticalProfile,
						autoCalled, step, verbose, test);
				if (rt1 == null)
					return 0;

				rt1.show("Results");
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable);

				UtilAyv.afterWork();
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	@SuppressWarnings("deprecation")
	public static ResultsTable mainUnifor(String[] path, int sqX, int sqY,
			boolean verticalProfile, boolean autoCalled, boolean step,
			boolean verbose, boolean test) {
		// public static ResultsTable mainUnifor(String[] path, int sqX, int
		// sqY,
		// String autoArgs, boolean verticalProfile, boolean autoCalled,
		// boolean step, boolean verbose, boolean test) {

		boolean accetta = false;
		ResultsTable rt = null;
		String simulataName = "";
		// MyLog.waitHere("verticalProfile=" + verticalProfile);
		//
		// MyLog.logVector(path, "path");

		UtilAyv.setMeasure(MEAN + STD_DEV);

		do {
			ImagePlus imp1 = null;
			ImagePlus imp2 = null;
			Overlay over2 = new Overlay();
			Overlay over3 = new Overlay();

			if (verbose) {
				imp1 = UtilAyv.openImageMaximized(path[0]);
				imp2 = UtilAyv.openImageNoDisplay(path[1], true);
			} else {
				imp1 = UtilAyv.openImageNoDisplay(path[0], true);
				imp2 = UtilAyv.openImageNoDisplay(path[1], true);
			}
			// MyLog.waitHere();

			ImagePlus imaDiff = UtilAyv.genImaDifference(imp1, imp2);
			if (verbose) {
				UtilAyv.showImageMaximized(imaDiff);
				// imp1.getWindow().toFront();
			}

			// MyLog.waitHere();

			int width = imp1.getWidth();
			int height = imp1.getHeight();

			overlayGrid(imp1, MyConst.P5_GRID_NUMBER, verbose);

			double dimPixel = ReadDicom.readDouble(ReadDicom.readSubstring(
					ReadDicom.readDicomParameter(imp1,
							MyConst.DICOM_PIXEL_SPACING), 2));
			if (verbose)
				imp1.setTitle("DIMENSIONI RETICOLO= "
						+ (dimPixel * (double) height / (double) MyConst.P5_GRID_NUMBER)
						+ " mm");

			int sqNEA = MyConst.P5_NEA_11X11_PIXEL;
			imp1.setOverlay(over2);
			over2.setStrokeColor(Color.red);

			ImageProcessor ip1 = imp1.getProcessor();
			//
			// disegno MROI su imp1
			//
			if (!test) {
				int posX = ReadDicom.readInt(Prefs.get("prefer.p5rmnPosX",
						Integer.toString(height / 2)));
				if ((posX < 10) || (posX > (height - 10)))
					posX = height / 2;
				int posY = ReadDicom.readInt(Prefs.get("prefer.p5rmnPosY",
						Integer.toString(width / 2)));
				if ((posY < 10) || (posY > (width - 10)))
					posY = width / 2;
				// UtilAyv.autoAdjust(imp1, ip1);
				if (imp1.isVisible())
					UtilAyv.backgroundEnhancement(0, 0, 10, imp1);
				int userSelection1 = 0;

				// TODO posizione di test

				// posX = 56;
				// posY = 111;
				// IJ.log(""+imp1.getShortTitle());
				// IJ.log(""+imp2.getShortTitle());

				do {
					imp1.setRoi(posX, posY, sqNEA, sqNEA);
					imp1.updateAndDraw();
					userSelection1 = menuPositionMroi();
				} while (userSelection1 == 1);
				//
				// rilettura posizione user-defined
				//
				// new WaitForUserDialog("Do something, then click OK.").show();
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.red);

				ip1 = imp1.getProcessor();
				Rectangle boundingRectangle = ip1.getRoi();

				imp1.getWindow().toFront();
				sqX = boundingRectangle.x;
				sqY = boundingRectangle.y;

				Prefs.set("prefer.p5rmnPosX",
						Integer.toString(boundingRectangle.x));
				Prefs.set("prefer.p5rmnPosY",
						Integer.toString(boundingRectangle.y));
			} else {
				imp1.setRoi(sqX, sqY, sqNEA, sqNEA);
				imp1.updateAndDraw();
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.red);
			}
			//
			// posiziono la ROI 7x7 all'interno di MROI
			//
			int gap = (sqNEA - MyConst.P5_MROI_7X7_PIXEL) / 2;
			imp1.setRoi(sqX + gap, sqY + gap, MyConst.P5_MROI_7X7_PIXEL,
					MyConst.P5_MROI_7X7_PIXEL);
			imp1.updateAndDraw();
			over2.addElement(imp1.getRoi());
			over2.setStrokeColor(Color.red);
			ImageStatistics stat1 = imp1.getStatistics();
			double signal1 = stat1.mean;

			int xFondo = MyConst.P5_X_ROI_BACKGROUND;
			int yFondo = MyConst.P5_Y_ROI_BACKGROUND;
			if (test)
				yFondo = yFondo + 40;
			if (step)
				msgMroi();
			//
			// disegno RoiFondo su imp1
			//
			ImageStatistics statBkg = UtilAyv.backCalc(xFondo, yFondo,
					MyConst.P5_DIAM_ROI_BACKGROUND, imp1, step, false, test);

			//
			// disegno MROI su imaDiff
			//
			// ImagePlus imaDiff = UtilAyv.genImaDifference(imp1, imp2);
			// if (verbose) {
			// UtilAyv.showImageMaximized(imaDiff);
			// // imp1.getWindow().toFront();
			// }
			overlayGrid(imaDiff, MyConst.P5_GRID_NUMBER, verbose);

			imaDiff.resetDisplayRange();
			imaDiff.setOverlay(over3);
			over3.setStrokeColor(Color.green);

			imaDiff.setRoi(sqX + gap, sqY + gap, MyConst.P5_MROI_7X7_PIXEL,
					MyConst.P5_MROI_7X7_PIXEL);
			over3.addElement(imaDiff.getRoi());
			over3.setStrokeColor(Color.green);
			imaDiff.updateAndDraw();

			// MyLog.waitHere("ROI DISEGNATE SU IMADIFF");

			ImageStatistics statImaDiff = imaDiff.getStatistics();
			imaDiff.updateAndDraw();
			if (imaDiff.isVisible())
				imaDiff.getWindow().toFront();

			if (step)
				msgMroi();
			//
			// calcolo P su imaDiff
			//
			double prelimImageNoiseEstimate_MROI = statImaDiff.stdDev
					/ Math.sqrt(2);

			if (step) {
				msgNea(prelimImageNoiseEstimate_MROI);
			}
			//
			// loop di calcolo NEA su imp1
			//
			imp1.setRoi(sqX, sqY, sqNEA, sqNEA);
			imp1.updateAndDraw();

			over2.addElement(imp1.getRoi());
			over2.setStrokeColor(Color.red);

			if (imp1.isVisible())
				imp1.getWindow().toFront();
			//
			// qui, se il numero dei pixel < 121 dovrò incrementare sqR2 e
			// ripetere il loop
			//
			double checkPixels = MyConst.P5_CHECK_PIXEL_MULTIPLICATOR
					* prelimImageNoiseEstimate_MROI;
			int area11x11 = MyConst.P5_NEA_11X11_PIXEL
					* MyConst.P5_NEA_11X11_PIXEL;
			int enlarge = 0;
			int pixx = 0;

			do {
				pixx = countPix(imp1, sqX - enlarge, sqY - enlarge, sqNEA,
						checkPixels);

				imp1.setRoi(sqX - enlarge, sqY - enlarge, sqNEA, sqNEA);
				imp1.updateAndDraw();
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.red);

				// imp1.getWindow().toFront();
				if (step)
					msgDisplayNEA();

				if (pixx < area11x11) {
					sqNEA = sqNEA + 2; // accrescimento area
					enlarge = enlarge + 1;
				}
				if (step) {
					msgEnlargeRoi(sqNEA);
				}

				// verifico che quando cresce il lato del quadrato non si esca
				// dall'immagine

				if ((sqX + sqNEA - enlarge) >= width || (sqX - enlarge) <= 0) {
					msgNot121();
					return null;
				}
				if ((sqY + sqNEA - enlarge) >= height || (sqY - enlarge) <= 0) {
					msgNot121();
					return null;
				}
				if (step && pixx >= area11x11)
					msgSqr2OK(pixx);

			} while (pixx < area11x11);

			imp1.setRoi(sqX - enlarge, sqY - enlarge, sqNEA, sqNEA);
			imp1.updateAndDraw();
			if (imp1.isVisible())
				imp1.getWindow().toFront();

			// MyLog.waitHere("verificare la roi");

			//
			// calcolo SD su imaDiff quando i corrispondenti pixel
			// di imp1 passano il test
			//

			boolean paintPixels = false;
			double[] out1 = devStandardNema(imp1, imaDiff, sqX - enlarge, sqY
					- enlarge, sqNEA, checkPixels, paintPixels);

			if (step)
				msgDisplayMean4(out1[0], out1[1]);
			//
			// calcolo SNR finale
			//

			double snr = signal1 / (out1[1] / Math.sqrt(2));
			if (step)
				msgSnr(snr);

			//
			// calcolo simulata
			//
			// int[][] classiSimulata = ImageUtils.generaSimulata12classi(sqX
			// + gap, sqY + gap, MyConst.P5_MROI_7X7_PIXEL, imp1, step,
			// verbose, test);

			String patName = ReadDicom.readDicomParameter(imp1,
					MyConst.DICOM_PATIENT_NAME);

			String codice1 = ReadDicom.readDicomParameter(imp1,
					MyConst.DICOM_SERIES_DESCRIPTION);

			String codice = UtilAyv.getFiveLetters(codice1);

			// String codice = ReadDicom
			// .readDicomParameter(imp1,
			// MyConst.DICOM_SERIES_DESCRIPTION)
			// .substring(0, 4).trim();

			simulataName = fileDir + patName + codice + "sim.zip";

			// int[][] classiSimulata = ImageUtils.generaSimulata12classi(sqX
			// + gap, sqY + gap, MyConst.P5_MROI_7X7_PIXEL, imp1, step,
			// verbose, test);

			int[][] classiSimulata = ImageUtils.generaSimulata12classi(sqX
					+ gap, sqY + gap, MyConst.P5_MROI_7X7_PIXEL, imp1,
					simulataName, step, verbose, test);
			//
			// calcolo posizione fwhm a metà della MROI
			//
			if (imp1.isVisible())
				imp1.getWindow().toFront();
			//
			// 28feb05 qui dò la possibilità di modificare la posizione su cui
			// verrà calcolato l'FWHM
			//
			int xStartProfile = 0;
			int yStartProfile = 0;
			int xEndProfile = 0;
			int yEndProfile = 0;

			if (test) {
				if (verticalProfile) {
					xStartProfile = sqX + gap + MyConst.P5_MROI_7X7_PIXEL / 2;
					yStartProfile = 1;
					xEndProfile = xStartProfile;
					yEndProfile = height;
				} else {
					xStartProfile = 1;
					yStartProfile = sqY + gap + MyConst.P5_MROI_7X7_PIXEL / 2;
					xEndProfile = width;
					yEndProfile = yStartProfile;
				}
			} else {
				Line line = selectProfilePosition(sqX + gap, sqY + gap,
						MyConst.P5_MROI_7X7_PIXEL, imp1, verticalProfile);

				xStartProfile = line.x1;
				yStartProfile = line.y1;
				xEndProfile = line.x2;
				yEndProfile = line.y2;

			}

			double[] outFwhm2 = analyzeProfile(imp1, xStartProfile,
					yStartProfile, xEndProfile, yEndProfile, dimPixel, step);

			//
			// Salvataggio dei risultati nella ResultsTable

			// String[][] tabCodici = new InputOutput().readFile1(
			// MyConst.CODE_FILE, MyConst.TOKENS4);

			String[][] tabCodici = TableCode.loadTableCSV(MyConst.CODE_FILE);

			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path[0],
					imp1, tabCodici, VERSION, autoCalled);

			//
			rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);
			int col = 2;
			String t1 = "TESTO          ";
			rt.setHeading(++col, "roi_x");
			rt.setHeading(++col, "roi_y");
			rt.setHeading(++col, "roi_b");
			rt.setHeading(++col, "roi_h");

			rt.addLabel(t1, simulataName);
			rt.incrementCounter();

			rt.addLabel(t1, "Segnale");
			rt.addValue(2, signal1);
			rt.addValue(3, sqX);
			rt.addValue(4, sqY);
			rt.addValue(5, sqNEA);
			rt.addValue(6, sqNEA);

			rt.incrementCounter();
			rt.addLabel(t1, "Rumore_Fondo222");
			rt.addValue(2, (out1[1] / Math.sqrt(2)));
			int xRoi = (int) statBkg.roiX;
			int yRoi = (int) statBkg.roiY;
			int widthRoi = (int) statBkg.roiWidth;
			int heightRoi = (int) statBkg.roiHeight;

			// IJ.log("xRoi= " + xRoi);

			rt.addValue(3, xRoi);
			rt.addValue(4, yRoi);
			rt.addValue(5, widthRoi);
			rt.addValue(6, heightRoi);

			rt.incrementCounter();
			rt.addLabel(t1, "SnR");
			rt.addValue(2, snr);
			rt.addValue(3, sqX);
			rt.addValue(4, sqY);
			rt.addValue(5, sqNEA);
			rt.addValue(6, sqNEA);

			rt.incrementCounter();
			rt.addLabel(t1, "FWHM");
			rt.addValue(2, outFwhm2[0]);
			rt.addValue(3, xStartProfile);
			rt.addValue(4, yStartProfile);
			rt.addValue(5, xEndProfile);
			rt.addValue(6, yEndProfile);
			
			rt.incrementCounter();
			rt.addLabel(t1, "Bkg");
			rt.addValue(2, statBkg.mean);
			rt.addValue(3, statBkg.roiX);
			rt.addValue(4, statBkg.roiY);
			rt.addValue(5, statBkg.roiWidth);
			rt.addValue(6, statBkg.roiHeight);


			String[] levelString = { "+20%", "+10%", "-10%", "-10%", "-30%",
					"-40%", "-50%", "-60%", "-70%", "-80%", "-90%", "fondo" };

			for (int i1 = 0; i1 < classiSimulata.length; i1++) {
				rt.incrementCounter();
				rt.addLabel(t1, ("Classe" + classiSimulata[i1][0]) + "_"
						+ levelString[i1]);
				rt.addValue(2, classiSimulata[i1][1]);
			}
			if (verbose && !test)
				rt.show("Results");

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
	 * Self test execution menu
	 */
	public void selfTestMenu() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			switch (userSelection2) {
			case 1: {
				// GE
				String[] list = { "CTSA2_01testP5", "CTSA2_02testP5",
						"CTSA2_03testP5" };
				String[] path = new InputOutput().findListTestImages2(
						MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

				int sqX = MyConst.P5_X_ROI_TESTGE;
				int sqY = MyConst.P5_Y_ROI_TESTGE;

				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;
				double[] vetReference = referenceGe();
				boolean verticalProfile = false;
				ResultsTable rt1 = mainUnifor(path, sqX, sqY, verticalProfile,
						autoCalled, step, verbose, test);
				if (rt1 == null)
					return;

				double[] vetResults = UtilAyv.vectorizeResults(rt1);
				boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
						MyConst.P5_vetName);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				break;
			}
			case 2:
				// Siemens
				String[] list = { "S1SA_01testP5", "S1SA_02testP5",
						"S1SA_03testP5" };
				String[] path = new InputOutput().findListTestImages2(
						MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

				int sqX = MyConst.P5_X_ROI_TESTSIEMENS;
				int sqY = MyConst.P5_Y_ROI_TESTSIEMENS;

				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;

				double[] vetReference = referenceSiemens();
				boolean verticalProfile = true;
				ResultsTable rt1 = mainUnifor(path, sqX, sqY, verticalProfile,
						autoCalled, step, verbose, test);
				// ResultsTable rt1 = mainUnifor(path, sqX, sqY, autoArgs,
				// verticalProfile, autoCalled, step, verbose, test);
				if (rt1 == null)
					return;
				double[] vetResults = UtilAyv.vectorizeResults(rt1);
				boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
						MyConst.P5_vetName);
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
	double[] referenceSiemens() {

		double simul = 0.0;
		double signal = 1538.5714285714287;
		// double backNoise = 7.87;
		double backNoise = 40.21194195114299;
		double snRatio = 38.26155499878056;
		double fwhm = 36.56299427908097;
		double num1 = 1798.0;
		double num2 = 527.0;
		double num3 = 1156.0;
		double num4 = 678.0;
		double num5 = 770.0;
		double num6 = 902.0;
		double num7 = 1044.0;
		double num8 = 1359.0;
		double num9 = 1792.0;
		double num10 = 2617.0;
		double num11 = 5287.0;
		double num12 = 47606.0;

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm,
				num1, num2, num3, num4, num5, num6, num7, num8, num9, num10,
				num11, num12 };
		return vetReference;

	}

	/**
	 * Ge test image expected results
	 * 
	 * @return
	 */
	double[] referenceGe() {

		double simul = 0.0;
		double signal = 2045.4897959183672;
		// double backNoise = 84.06;
		double backNoise = 21.947567747419033;
		double snRatio = 93.19892843975435;
		double fwhm = 32.93099254935456;
		double num1 = 2262.0;
		double num2 = 521.0;
		double num3 = 1427.0;
		double num4 = 1038.0;
		double num5 = 1430.0;
		double num6 = 2307.0;
		double num7 = 3178.0;
		double num8 = 2365.0;
		double num9 = 2149.0;
		double num10 = 2110.0;
		double num11 = 948.0;
		double num12 = 45801.0;

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm,
				num1, num2, num3, num4, num5, num6, num7, num8, num9, num10,
				num11, num12 };
		return vetReference;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {
		String[] list = { "S1SA_01testP5", "S1SA_02testP5", "S1SA_03testP5" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		double[] vetReference = referenceSiemens();

		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;
		int sqX = MyConst.P5_X_ROI_TESTSIEMENS;
		int sqY = MyConst.P5_Y_ROI_TESTSIEMENS;
		boolean verticalProfile = true;

		ResultsTable rt1 = p5rmn_.mainUnifor(path, sqX, sqY, verticalProfile,
				autoCalled, step, verbose, test);
		// ResultsTable rt1 = p5rmn_.mainUnifor(path, sqX, sqY, autoArgs,
		// verticalProfile, autoCalled, step, verbose, test);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P5_vetName);
		if (ok) {
			IJ.log("Il test di p5rmn_ UNIFORMITA' SUPERFICIALE è stato SUPERATO");
		} else {
			IJ.log("Il test di p5rmn_ UNIFORMITA' SUPERFICIALE evidenzia degli ERRORI");
		}
		return;

	}

	/**
	 * Calcola la deviazione standard
	 * 
	 * @param num
	 *            Numero dei pixel
	 * @param sum
	 *            Somma dei valori pixel
	 * @param sum2
	 *            Somma dei quadrati dei valori dei pixel
	 * @return deviazione standard
	 */

	private static double calculateStdDev4(int num, double sum, double sum2) {
		double sd1;
		if (num > 0) {
			sd1 = (num * sum2 - sum * sum) / num;
			if (sd1 > 0.0)
				sd1 = Math.sqrt(sd1 / (num - 1.0));
			else
				sd1 = 0.0;
		} else
			sd1 = 0.0;
		return (sd1);
	}

	/**
	 * Conta i pixel che oltrepassano la soglia di conteggio
	 * 
	 * @param imp1
	 *            immagine in input
	 * @param sqX
	 *            coordinata della Roi
	 * @param sqY
	 *            coordinata della Roi
	 * @param sqR
	 *            lato della Roi
	 * @param limit
	 *            soglia di conteggio
	 * @return pixel che superano la soglia
	 */
	private static int countPix(ImagePlus imp1, int sqX, int sqY, int sqR,
			double limit) {
		int offset = 0;
		int w = 0;
		int count1 = 0;

		if (imp1 == null) {
			IJ.error("CountPix ricevuto null");
			return (0);
		}

		int width = imp1.getWidth();
		short[] pixels1 = UtilAyv.truePixels(imp1);
		for (int y1 = sqY; y1 < (sqY + sqR); y1++) {
			offset = y1 * width;
			for (int x1 = sqX; x1 < (sqX + sqR); x1++) {
				w = offset + x1;
				if (pixels1[w] > limit) {
					count1++;
				}
			}
		}
		return count1;
	}

	/**
	 * Effettua il calcolo della deviazione standard per i pixel della immagine
	 * differenza i cui corrispondenti pixel della prima immagine oltrepassano
	 * la soglia di conteggio, secondo il protocollo NEMA
	 * 
	 * @param imp1
	 *            immagine in input
	 * @param imp3
	 *            immagine differenza
	 * @param sqX
	 *            coordinata della Roi
	 * @param sqY
	 *            coordinata della Roi
	 * @param sqR
	 *            lato della Roi
	 * @param limit
	 *            soglia di conteggio
	 * @param paintPixels
	 *            ATTENZIONE da usare solo per test, altera i risultati !!!!! VA
	 *            TENUTO FALSE
	 * 
	 * @return [0] sum / pixelcount [1] devStan
	 */

	/***
	 * 
	 * @param imp1
	 * @param imp3
	 * @param sqX
	 * @param sqY
	 * @param sqR
	 * @param limit
	 * @return
	 */
	public static double[] devStandardNema(ImagePlus imp1, ImagePlus imp3,
			int sqX, int sqY, int sqR, double limit, boolean paintPixels) {
		double[] results = new double[2];
		double value4 = 0.0;
		double sumValues = 0.0;
		double sumSquare = 0.0;

		if ((imp1 == null) || (imp3 == null)) {
			IJ.error("devStandardNema ricevuto null");
			return (null);
		}
		int width = imp1.getWidth();
		short[] pixels1 = UtilAyv.truePixels(imp1);
		short[] pixels2 = null;

		ImageProcessor ip1 = imp1.getProcessor();
		if (paintPixels) {
			pixels2 = (short[]) ip1.getPixels();
		}

		int pixelCount = 0;
		int offset = 0;
		ImageProcessor ip3 = imp3.getProcessor();
		float[] pixels4 = (float[]) ip3.getPixels();
		for (int y1 = sqY; y1 < (sqY + sqR); y1++) {
			for (int x1 = sqX; x1 < (sqX + sqR); x1++) {
				offset = y1 * width + x1;
				if (pixels1[offset] > limit) {
					pixelCount++;
					value4 = pixels4[offset];
					sumValues += value4;
					sumSquare += value4 * value4;
					// IJ.log("p5 x1=" + x1 + " y1=" + y1 + " s1=" + value4);
					if (paintPixels)
						pixels2[offset] = 4096;
				}
			}
		}
		imp1.updateAndDraw();
		results[0] = sumValues / pixelCount;
		double sd1 = calculateStdDev4(pixelCount, sumValues, sumSquare);
		results[1] = sd1;
		return (results);
	}

	/**
	 * Analisi di un profilo NON mediato
	 * 
	 * @param imp1
	 *            Immagine da analizzare
	 * @param ax
	 *            Coordinata x inizio segmento
	 * @param ay
	 *            Coordinata y inizio segmento
	 * @param bx
	 *            Coordinata x fine segmento
	 * @param by
	 *            Coordinata x fine segmento
	 * 
	 * @return outFwhm[0]=FWHM, outFwhm[1]=peak position
	 */

	private static double[] analyzeProfile(ImagePlus imp1, int ax, int ay,
			int bx, int by, double dimPixel, boolean step) {

		if (imp1 == null) {
			IJ.error("analProf2 ricevuto null");
			return (null);
		}

		imp1.setRoi(new Line((int) ax, (int) ay, (int) bx, (int) by));
		Roi roi1 = imp1.getRoi();

		double[] profi1 = ((Line) roi1).getPixels(); // profilo non mediato

		/*
		 * le seguenti istruzioni sono state superate dalla release 1.40a di
		 * ImageJ. Tale cambiamento è dovuto alle modifiche apportate a
		 * ij\ImagePlus.java, in pratica se l'immagine è calibrata la
		 * calibrazione viene automaticamente applicata anche ad ImagePlus
		 */

		// Calibration cal = imp1.getCalibration();
		// if (utils.versionLess("1.40a")) {
		// for (int i1 = 0; i1 < profi1.length; i1++) {
		// profi1[i1] = cal.getCValue(profi1[i1]);
		// }
		// }
		profi1[profi1.length - 1] = 0; // azzero a mano l'ultimo pixel

		if (step) {
			imp1.updateAndDraw();
			ButtonMessages.ModelessMsg("Profilo non mediato  <50>", "CONTINUA");
		}

		int vetHalfPoint[];
		double[] outFwhm;
		vetHalfPoint = halfPointSearch(profi1);
		outFwhm = calcFwhm(vetHalfPoint, profi1, dimPixel);
		if (step)

			createPlot(profi1, true, true); // plot della fwhm
		if (step)
			ButtonMessages.ModelessMsg("Continuare?   <51>", "CONTINUA");
		return (outFwhm);
	} // analProf2

	/**
	 * Calcolo dell'FWHM su di un vettore profilo
	 * 
	 * @param vetUpDwPoints
	 *            Vettore restituito da AnalPlot2 con le posizioni dei punti
	 *            sopra e sotto la metà altezza
	 * @param profile
	 *            Profilo da analizzare
	 * @return out[0]=FWHM, out[1]=peak position
	 */

	private static double[] calcFwhm(int[] vetUpDwPoints, double profile[],
			double dimPixel) {

		double peak = 0;
		double[] a = Tools.getMinMax(profile);
		double min = a[0];
		double max = a[1];

		// interpolazione lineare sinistra
		double px0 = vetUpDwPoints[0];
		double px1 = vetUpDwPoints[1];
		double px2;
		double py0 = profile[vetUpDwPoints[0]];
		double py1 = profile[vetUpDwPoints[1]];
		double py2 = (max - min) / 2.0 + min;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);

		double sx = px2;

		// interpolazione lineare destra
		px0 = vetUpDwPoints[2];
		px1 = vetUpDwPoints[3];
		py0 = profile[vetUpDwPoints[2]];
		py1 = profile[vetUpDwPoints[3]];
		py2 = (max - min) / 2.0 + min;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double dx = px2;
		double fwhm = (dx - sx) * dimPixel; // in mm
		// double fwhm = (dx - sx);

		for (int i1 = 0; i1 < profile.length; i1++) {
			if (profile[i1] == min)
				peak = i1;
		}

		double[] out = { fwhm, peak };
		return (out);
	} // calcFwhm

	/**
	 * Mostra a video un profilo con linea a metà picco
	 * 
	 * @param profile1
	 *            Vettore con il profilo da analizzare
	 * @param bslab
	 *            Flag slab che qui mettiamo sempre true
	 */

	private static void createPlot(double profile1[], boolean bslab,
			boolean bLabelSx) {

		int[] vetUpDwPoints = halfPointSearch(profile1);
		double[] a = Tools.getMinMax(profile1);
		double min = a[0];
		double max = a[1];
		double half = (max - min) / 2.0 + min;

		double[] xVectPointsX = new double[2];
		double[] yVectPointsX = new double[2];

		double[] xVectPointsO = new double[2];
		double[] yVectPointsO = new double[2];

		double[] xVetLineHalf = new double[2];
		double[] yVetLineHalf = new double[2];
		int len1 = profile1.length;
		double[] xcoord1 = new double[len1];
		for (int j = 0; j < len1; j++)
			xcoord1[j] = j;
		// PlotWindow plot = new PlotWindow("Plot profilo penetrazione",
		// "pixel",
		// "valore", xcoord1, profile1);
		Plot plot = new Plot("Plot profilo penetrazione", "pixel", "valore",
				xcoord1, profile1);
		if (bslab)
			plot.setLimits(0, len1, min, max);
		else
			plot.setLimits(0, len1, min, 30);
		plot.setLineWidth(1);
		plot.setColor(Color.blue);
		xVectPointsX[0] = (double) vetUpDwPoints[0];
		xVectPointsX[1] = (double) vetUpDwPoints[2];
		yVectPointsX[0] = profile1[vetUpDwPoints[0]];
		yVectPointsX[1] = profile1[vetUpDwPoints[2]];
		plot.addPoints(xVectPointsX, yVectPointsX, PlotWindow.X);
		xVectPointsO[0] = (double) vetUpDwPoints[1];
		xVectPointsO[1] = (double) vetUpDwPoints[3];
		yVectPointsO[0] = profile1[vetUpDwPoints[1]];
		yVectPointsO[1] = profile1[vetUpDwPoints[3]];
		plot.addPoints(xVectPointsO, yVectPointsO, PlotWindow.CIRCLE);
		plot.changeFont(new Font("Helvetica", Font.PLAIN, 10));

		// interpolazione lineare sinistra
		double px0 = vetUpDwPoints[0];
		double px1 = vetUpDwPoints[1];
		double px2 = 0;
		double py0 = profile1[vetUpDwPoints[0]];
		double py1 = profile1[vetUpDwPoints[1]];
		double py2 = 0;
		py2 = half;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double sx = px2;
		// interpolazione lineare destra
		px0 = vetUpDwPoints[2];
		px1 = vetUpDwPoints[3];
		py0 = profile1[vetUpDwPoints[2]];
		py1 = profile1[vetUpDwPoints[3]];
		py2 = half;
		px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		double dx = px2;
		double fwhm = dx - sx;
		double labelPosition = 0;

		if (bLabelSx)
			labelPosition = 0.10;
		else
			labelPosition = 0.60;
		plot.addLabel(labelPosition, 0.45, "peak / 2=   " + IJ.d2s(max / 2, 2));
		plot.addLabel(labelPosition, 0.50, "down sx " + vetUpDwPoints[0]
				+ "  =   " + IJ.d2s(profile1[vetUpDwPoints[0]], 2));
		plot.addLabel(labelPosition, 0.55, "down dx " + vetUpDwPoints[2]
				+ "  =   " + IJ.d2s(profile1[vetUpDwPoints[2]], 2));
		plot.addLabel(labelPosition, 0.60, "up      sx " + vetUpDwPoints[1]
				+ "  =   " + IJ.d2s(profile1[vetUpDwPoints[1]], 2));
		plot.addLabel(labelPosition, 0.65, "up      dx " + vetUpDwPoints[3]
				+ "  =   " + IJ.d2s(profile1[vetUpDwPoints[3]], 2));
		plot.addLabel(labelPosition, 0.70,
				"sx interp       =  " + IJ.d2s(sx, 2));
		plot.addLabel(labelPosition, 0.75,
				"dx interp       =  " + IJ.d2s(dx, 2));
		plot.addLabel(labelPosition, 0.80,
				"fwhm            =  " + IJ.d2s(fwhm, 2));
		plot.setColor(Color.green);
		xVetLineHalf[0] = 0;
		xVetLineHalf[1] = len1;
		yVetLineHalf[0] = half;
		yVetLineHalf[1] = half;
		plot.addPoints(xVetLineHalf, yVetLineHalf, PlotWindow.LINE);
		plot.setColor(Color.red);
		plot.show();

		plot.draw();
	} // createPlot2

	/**
	 * ricerca dei punti a metà altezza
	 * 
	 * @param profile1
	 *            Vettore con il profilo da analizzare
	 * @return isd[0] punto sotto half a sx, isd[1] punto sopra half a sx,
	 * @return isd[2] punto sotto half a dx, isd[3] punto sopra half a dx
	 */
	private static int[] halfPointSearch(double profile1[]) {
		/*
		 * ATTENZIONE. il nostro profilo standard è il seguente:
		 * 
		 * ........... .......... max . . upSx * * upDx
		 * -------------.--------------.------------ half downSx * * downDx . .
		 * ......... min
		 */

		int upSx = 0;
		int downSx = 0;
		int upDx = 0;
		int downDx = 0;
		double max = 0;
		int i1;

		int[] vetHalfPoint = new int[4];
		vetHalfPoint[0] = 0;
		vetHalfPoint[1] = 0;
		vetHalfPoint[2] = 0;
		vetHalfPoint[3] = 0;
		int len1 = profile1.length;

		// String str = "";
		// for (int i2 = 0; i2 < len1; i2++)
		// str = str + profile1[i2] + " ";
		// IJ.log("pixels profile1=" + str);

		double[] a = Tools.getMinMax(profile1);
		double min = a[0];

		max = a[1];

		// calcolo metà altezza
		double half = (max - min) / 2 + min;
		// parto da sx e percorro il profilo in cerca del primo valore che
		// supera half
		for (i1 = 0; i1 < len1 - 1; i1++) {
			if (profile1[i1] > half) {
				downSx = i1;
				break;
			}
		}
		// torno indietro e cerco il primo valore sotto half
		for (i1 = downSx; i1 > 0; i1--) {
			if (profile1[i1] < half) {
				upSx = i1;
				break;
			}
		}
		// parto da dx e percorro il profilo in cerca del primo valore che
		// supera half
		for (i1 = len1 - 1; i1 > 0; i1--) {
			if (profile1[i1] > half) {
				downDx = i1;
				break;
			}
		}
		// torno indietro e cerco il primo valore sotto half
		for (i1 = downDx; i1 < len1 - 1; i1++) {
			if (profile1[i1] < half) {
				upDx = i1;
				break;
			}
		}
		vetHalfPoint[0] = downSx;
		vetHalfPoint[1] = upSx;
		vetHalfPoint[2] = downDx;
		vetHalfPoint[3] = upDx;
		return vetHalfPoint;
	} // halfPointSerach

	/**
	 * scelta da parte dell'utente della posizione e direzione del profilo su
	 * cui poi verrà calcolata l'FWHM
	 * 
	 * @param xPos
	 *            posizione x MROI
	 * @param yPos
	 *            posizione y MROI
	 * @param len
	 *            lato MROI
	 * @param imp1
	 *            puntatore ImagePlus alla immagine originale
	 * @return line parametri profilo selezionato
	 */
	private static Line selectProfilePosition(int xPos, int yPos, int len,
			ImagePlus imp1, boolean profiVert) {

		// partiamo da dove è stata posizionata la ROI

		int xStartProfile = 0;
		int yStartProfile = 0;
		int xEndProfile = 0;
		int yEndProfile = 0;
		boolean continua = false;

		int width = imp1.getWidth();
		int height = imp1.getHeight();
		// per la direzione del profilo utilizziamo l'ultima selezionata
		// boolean profiVert = Prefs.get("prefer.p5rmnVert", true);
		do {
			if (profiVert) {
				xStartProfile = xPos + len / 2;
				yStartProfile = 1;
				xEndProfile = xPos + len / 2;
				yEndProfile = height;
			} else {
				xStartProfile = 1;
				yStartProfile = yPos + len / 2;
				xEndProfile = width;
				yEndProfile = yPos + len / 2;
			}
			imp1.setRoi(new Line((int) xStartProfile, (int) yStartProfile,
					(int) xEndProfile, (int) yEndProfile));
			imp1.updateAndDraw();

			// effettuo la scelta all'interno del loop, in modo da poterla
			// ripetere
			int userSelection1 = ButtonMessages.ModelessMsg(
					"Linea su cui verrà calcolata la FWHM, eventualmente"
							+ " riposizionarla e premere CONTINUA   <24>",
					"CONTINUA", "ORIZZ", "VERT");

			switch (userSelection1) {
			case 1:
				profiVert = true;
				break;
			case 2:
				profiVert = false;
				break;
			case 3:
				continua = true;
				break;
			}
		} while (!continua);

		Prefs.set("prefer.p5rmnVert", profiVert);
		//
		// rilettura posizione user-defined linea
		//
		Roi roi = imp1.getRoi();
		Line line = (Line) roi;
		return line;
	}

	public static void overlayGrid(ImagePlus imp1, int gridNumber,
			boolean verbose) {

		CustomCanvasGeneric ccg1 = new CustomCanvasGeneric(imp1);
		ccg1.setGridElements(gridNumber);
		if (verbose) {
			ImageWindow iw1 = new ImageWindow(imp1, ccg1);
			iw1.maximize();
		}
	}

	private static void msgNot121() {
		IJ.showMessage("ATTENZIONE la NEA esce dall'immagine\nsenza"
				+ " riuscire a trovare 121 pixel che superino il"
				+ " test\nil programma TERMINA PREMATURAMENTE");
	}

	private static void msgMroi() {
		ButtonMessages.ModelessMsg("Disegnata Mroi su imaDiff", "CONTINUA");
	}

	private static void msgNea(double noise) {
		ButtonMessages.ModelessMsg("P=" + noise
				+ "  (Preliminary Noise Estimate MROI ima4)", "CONTINUA");
	}

	private static void msgEnlargeRoi(int sqNEA) {
		ButtonMessages.ModelessMsg("Accrescimento MROI lato=" + sqNEA,
				"CONTINUA");
	}

	private static void msgSqr2OK(int pixx) {
		ButtonMessages.ModelessMsg("sqR2 OK  poichè pixx=" + pixx, "CONTINUA");
	}

	private static void msgSnr(double snr) {
		ButtonMessages.ModelessMsg("SNR finale=" + snr, "CONTINUA");
	}

	private static void msgDisplayMean4(double mean, double stdDev) {
		ButtonMessages.ModelessMsg("mean4= " + mean + " standard_deviation4= "
				+ stdDev, "CONTINUA");
	}

	private static void msgDisplayNEA() {
		ButtonMessages.ModelessMsg("displayNEA", "CONTINUA");
	}

	private static void msgSimulata() {
		ButtonMessages.ModelessMsg("Immagine Simulata", "CONTINUA");
	}

	private static int menuPositionMroi() {
		int userSelection1 = ButtonMessages.ModelessMsg(
				"Posizionare la MROI sull'area della bobina"
						+ "  e premere Accetta", "ACCETTA", "RIDISEGNA");
		return userSelection1;
	}

	public static boolean decodeDirez(String in1) {
		boolean out = false;
		String aux1 = in1.substring(0, 1);
		if (aux1.equals("v") || aux1.equals("V")) {
			out = false;
		} else if (aux1.equals("h") || aux1.equals("H")) {
			out = true;
		}
		return out;
	}
} // p5rmn_
