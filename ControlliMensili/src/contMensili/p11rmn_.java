package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.CustomCanvasGeneric;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyConst;
import utils.MyFileLogger;
import utils.MyFilter;
import utils.MyFwhm;
import utils.MyInput;
import utils.MyLog;
import utils.MyMsg;
import utils.MyPlot;
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
 * Analizza in maniera automatica o semi-automatica UNIFORMITA', SNR, FWHM per
 * le bobine superficiali "piatte"
 * 
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */
public class p11rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "UNIFORMITA', SNR, FWHM per bobine superficiali automatico";

	private String TYPE = " >> CONTROLLO SUPERFICIALI UNCOMBINED_PIATTE";

	// ---------------------------"01234567890123456789012345678901234567890"

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.xls
	 */
	private static String fileDir = "";
	public static String simpath = "";
	private static String simulataName = "";

	// private static boolean previous = false;
	// private static boolean init1 = true;
	// private static boolean pulse = false; // lasciare, serve anche se
	// segnalato
	// inutilizzato
	private static Color color1 = Color.green;
	private static Color color2 = Color.green;
	private static final boolean debug = true;
	public static boolean forcesilent = false;

	// private boolean profiVert = false;

	public void run(String args) {

		String className = this.getClass().getName();

		UtilAyv.setMyPrecision();

		Count c1 = new Count();
		if (!c1.jarCount("iw2ayv_"))
			return;

		// VERSION = className + "_build_"
		// + ReadVersion.readVersionInfoInManifest("contMensili")
		// + "_iw2ayv_build_"
		// + ReadVersion.readVersionInfoInManifest("utils");

//		String className = this.getClass().getName();
		String user1 = System.getProperty("user.name");
		TableCode tc1 = new TableCode();
		String iw2ayv1 = tc1.nameTable("codici", "csv");
		TableExpand tc2 = new TableExpand();
		String iw2ayv2 = tc1.nameTable("expand", "csv");

		VERSION = user1 + ":" + className + "build_" + MyVersion.getVersion() + ":iw2ayv_build_"
				+ MyVersionUtils.getVersion() + ":" + iw2ayv1 + ":" + iw2ayv2;

		// VERSION = className + "_build_" + MyVersion.getVersion() + "_iw2ayv_build_" +
		// MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");

		if (IJ.versionLessThan("1.43k"))
			return;

		// MyFileLogger.logger.info("p11rmn_rmn_>>> fileDir = " + fileDir);

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
		int timeout = 0;
		int mode = 0;
		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			// MyLog.waitHere("MANUAL userSelection1=" + userSelection1);

			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox().about("Controllo Bobine Superficiali",
				// this.getClass());
				new AboutBox().about("Controllo Bobine Superficiali", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				boolean verbose = true;
				timeout = MyInput.myIntegerInput("Ritardo avanzamento (0 = infinito)", "      [msec]", 1000, 0);

				selfTestMenu(verbose, timeout);

				retry = true;
				break;
			case 4:
				mode = 3;
				// step = true;
			case 5:
				if (mode == 0)
					mode = 2;
				String path1 = UtilAyv.imageSelection("SELEZIONARE PRIMA ACQUISIZIONE PRIMO ECO...");
				if (path1 == null)
					return 0;

				String path2 = UtilAyv.imageSelection("SELEZIONARE SECONDA ACQUISIZIONE PRIMO ECO...");
				if (path2 == null)
					return 0;

				String path3 = UtilAyv.imageSelection("SELEZIONARE PRIMA  ACQUISIZIONE SECONDO ECO...");
				if (path3 == null)
					return 0;

				int direzione = 1;
				// boolean autoCalled = false;
				// boolean verbose = false;
				// boolean test = false;
				double profond = 30.0;
				mode = 5;

				// boolean fast = false;
				// boolean silent = false;
				ResultsTable rt1 = mainUnifor(path1, path2, direzione, profond, "", mode, timeout);

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

		int timeout = 0;

		MyLog.appendLog(fileDir + "MyLog.txt", "p11 riceve " + autoArgs);
		boolean fast = Prefs.get("prefer.fast", "false").equals("true") ? true : false;
		// IJ.log("p11rmn_.autoMenu fast= " + fast);

		// IJ.log("p11rmn_.autoMenu autoargs= " + autoArgs);
		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p11rmn_");
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
		String path3 = "";
		String path4 = "";

		if (nTokens == MyConst.TOKENS2) {
			// UtilAyv.checkImages(vetRiga, iw2ayvTable, 2, debug);
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			UtilAyv.checkImages2(path1, path2, debug);

			MyLog.logDebug(vetRiga[0], "P11", fileDir);
			MyLog.logDebug(vetRiga[1], "P11", fileDir);
			// path3 = lr.getPath(strRiga3, riga2);
		} else {
			// UtilAyv.checkImages(vetRiga, iw2ayvTable, 3, debug);
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
			path3 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			path4 = TableSequence.getPath(iw2ayvTable, vetRiga[3]);
			UtilAyv.checkImages4(path1, path2, path3, path4, debug);

			MyLog.logDebug(vetRiga[0], "P11", fileDir);
			MyLog.logDebug(vetRiga[2], "P11", fileDir);
			MyLog.logDebug(vetRiga[1], "P11", fileDir);
		}

		int direzione = decodeDirezione(TableSequence.getDirez(iw2ayvTable, vetRiga[0]));

		double profond = Double.parseDouble(TableSequence.getProfond(iw2ayvTable, vetRiga[0]));

		if (UtilAyv.isNaN(profond)) {
			MyLog.logVector(iw2ayvTable[vetRiga[0]], "stringa");
			MyLog.waitHere();
		}

		boolean retry = false;

		int mode = 0;
		if (fast) {
			retry = false;
			mode = 1;
			if (forcesilent)
				mode = 0;
			// boolean autoCalled = true;
			// TODO ripristinare verbose=false
			// boolean verbose = false;
			// boolean verbose = true;
			// boolean test = false;
			// boolean silent = false;

			String info11 = "code= " + TableSequence.getCode(iw2ayvTable, vetRiga[0]) + " coil= "
					+ TableSequence.getCoil(iw2ayvTable, vetRiga[0]) + "  " + (vetRiga[0] + 1) + " / "
					+ TableSequence.getLength(iw2ayvTable);

			ResultsTable rt1 = mainUnifor(path1, path2, direzione, profond, info11, mode, timeout);

			if (rt1 == null) {
				ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
				TableCode tc1 = new TableCode();
				String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");
				String[] info12 = ReportStandardInfo.getSimpleStandardInfo(path1, imp11, tabCodici, VERSION, true);
				rt1 = ReportStandardInfo.abortResultTable_P11(info12);
			}

			// rt1.show("Results");
			UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt1);

			UtilAyv.afterWork();
		} else
			do {
				// int userSelection1 = UtilAyv.userSelectionAuto(VERSION,
				// TYPE);
				int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));
				MyLog.waitHere("AUTO userSelection1=" + userSelection1);

				switch (userSelection1) {
				case ABORT:
					new AboutBox().close();
					return 0;
				case 2:
					// new AboutBox().about("Controllo Bobine Superficiali",
					// this.getClass());
					new AboutBox().about("Controllo Bobine Superficiali", MyVersion.CURRENT_VERSION);
					retry = true;
					break;
				case 3:
					mode = 3;
					// retry = false;
					// break;
				case 4:
					// step = false;
					retry = false;
					// boolean autoCalled = true;
					// boolean verbose = true;
					// boolean test = false;
					// boolean silent = false;

					ResultsTable rt1 = mainUnifor(path1, path2, direzione, profond, "", mode, timeout);
					if (rt1 == null) {
						MyLog.waitHere("processo annulla");
						ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
						TableCode tc1 = new TableCode();
						String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");
						String[] info11 = ReportStandardInfo.getSimpleStandardInfo(path1, imp11, tabCodici, VERSION,
								true);
						rt1 = ReportStandardInfo.abortResultTable_P11(info11);
					}

					// rt1.show("Results");
					UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt1);

					UtilAyv.afterWork();
					break;
				}
			} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	/***
	 * introdotto il parametro integer mode, per riunire i flag booleani: step,
	 * verbose, test, fast----------------------------------------------------- mode
	 * = 0 silent ------------------------------------- ------------------ mode = 1
	 * auto fast ---------------------------------------------------- mode = 2
	 * manuale------------------------------------------------------- mode = 3
	 * manuale passo per passo (step)-------------------------------- mode = 4
	 * -------------------------------------------------------------- mode = 10 test
	 * automatico con immagini Siemens o Ge--------------------
	 * 
	 * @param path1
	 * @param path2
	 * @param direzione
	 * @param profond
	 * @param info10
	 * @param mode      0=silent, 1=fast, 2=step, 3=step, 4=verbose, 10=step
	 * @param timeout
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public static ResultsTable mainUnifor(String path1, String path2, int direzione, double profond, String info10,
			int mode, int timeout) {
		boolean accetta = false;
		boolean manualRequired2 = false;
		ResultsTable rt = null;
		boolean fast2 = false;

		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		boolean fast = false;
		boolean silent = false;

		switch (mode) {
		case 0:
			silent = true;
			break;
		case 1:
			autoCalled = true;
			verbose = true;
			fast = true;
			break;
		case 2:
			verbose = true;
			step = true;
			break;
		case 3:
			verbose = true;
			step = true;
			break;
		case 4:
			verbose = true;
			step = true;
			break;
		case 5:
			verbose = true;
			step = true;
			break;
		case 10:
			verbose = true;
			test = true;
			break;
		}

		UtilAyv.setMeasure(MEAN + STD_DEV);
		do {
			ImagePlus imp11;
			if ((fast && !manualRequired2) || silent)
				imp11 = UtilAyv.openImageNoDisplay(path1, true);
			// imp11 = UtilAyv.openImageMaximized(path1);
			else
				imp11 = UtilAyv.openImageMaximized(path1);

			if (imp11 == null)
				MyLog.waitHere("Non trovato il file " + path1);

			fast2 = fast && !manualRequired2;

			double out2[] = positionSearch(imp11, autoCalled, direzione, profond, info10, mode, timeout);

			imp11.close();
			
			

			if (out2 == null) {
				manualRequired2 = true;
			} else {
				manualRequired2 = false;
				ImagePlus imp1 = null;
				ImagePlus imp2 = null;

				if (verbose && !silent) {
					imp1 = UtilAyv.openImageMaximized(path1);
					imp2 = UtilAyv.openImageNoDisplay(path2, true);
				} else {
					imp1 = UtilAyv.openImageNoDisplay(path1, true);
					imp2 = UtilAyv.openImageNoDisplay(path2, true);
				}

				Overlay over2 = new Overlay();
				imp1.setOverlay(over2);

				Overlay over3 = new Overlay();

				int width = imp1.getWidth();
				int height = imp1.getHeight();

				double dimPixel = ReadDicom.readDouble(
						ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 2));

				int sqNEA = MyConst.P11_NEA_11X11_PIXEL;

				int xMaximum = (int) out2[6];
				int yMaximum = (int) out2[7];
				double xStartRefLine = out2[2];
				double yStartRefLine = out2[3];
				double xEndRefLine = out2[4];
				double yEndRefLine = out2[5];
				int xCenterRoi = (int) out2[0];
				int yCenterRoi = (int) out2[1];
				boolean confirmed = false;

				// =================================================

				if (verbose && !silent) {
					imp1.setRoi(new OvalRoi(xMaximum - 4, yMaximum - 4, 8, 8));
					ImageUtils.addOverlayRoi(imp1, Color.green, 0);
					imp1.killRoi();
					imp1.setRoi(new Line(xStartRefLine, yStartRefLine, xEndRefLine, yEndRefLine));
					ImageUtils.addOverlayRoi(imp1, Color.green, 0);
					imp1.killRoi();
					// imp1.setRoi(xCenterRoi - 10, yCenterRoi - 10, 20, 20);
					// ImageUtils.addOverlayRoi(imp1, Color.green, 0);
					// imp1.killRoi();
					// imp1.updateAndDraw();
				}

				//
				// disegno MROI su imp1
				//

				imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA, sqNEA);

				imp1.getRoi().setName("NEA");
				ImageUtils.addOverlayRoi(imp1, Color.blue, 1.01);
				imp1.killRoi();
				imp1.updateAndDraw();

				// =================================================

				double xStartRefLine2 = 0;
				double yStartRefLine2 = 0;
				double xEndRefLine2 = 0;
				double yEndRefLine2 = 0;

				if (direzione < 3) {
					xStartRefLine2 = xCenterRoi;
					yStartRefLine2 = 0;
					xEndRefLine2 = xCenterRoi;
					yEndRefLine2 = height;
				} else {

					xStartRefLine2 = 0;
					yStartRefLine2 = yCenterRoi;
					xEndRefLine2 = width;
					yEndRefLine2 = yCenterRoi;
				}

				imp1.setRoi(new Line(xStartRefLine2, yStartRefLine2, xEndRefLine2, yEndRefLine2));

				ImageUtils.addOverlayRoi(imp1, Color.green, 0);
				imp1.killRoi();
				if (!silent)
					imp1.updateAndDraw();
				if (step)
					MyLog.waitHere();

				//
				// posiziono la ROI 7x7 all'interno di MROI
				//

				int sq7 = MyConst.P11_MROI_7X7_PIXEL;

				imp1.setRoi(xCenterRoi - sq7 / 2, yCenterRoi - sq7 / 2, sq7, sq7);

				if (!silent && !fast) {
					// ------------------------
					ImageUtils.addOverlayRoi(imp1, Color.red, 1.01);
					imp1.getRoi().setName("MROI");
					imp1.killRoi();
					imp1.updateAndDraw();
					MyLog.waitHere("Modo Manuale", debug, timeout);

					// ------------------------
					imp1.setRoi(xCenterRoi - sq7 / 2, yCenterRoi - sq7 / 2, sq7, sq7);

				}

				if (step)
					MyLog.waitHere("posizione MROI");

				ImageStatistics stat1 = imp1.getStatistics();
				double signal1 = stat1.mean;

				double xFondo = imp1.getWidth() - MyConst.P11_X_ROI_BACKGROUND;
				double yFondo = MyConst.P11_Y_ROI_BACKGROUND + 5;
				boolean irraggiungibile = false;
				int diamBkg = MyConst.P11_DIAM_ROI_BACKGROUND;
				int guard = 10;
				boolean circle = false;
				double[] circleData = null;
				int select = 1;
				mode = 1;

				double[] backPos = UtilAyv.positionSearch15(imp1, circleData, xFondo, yFondo, diamBkg, guard, select,
						info10, circle, mode, irraggiungibile);

				xFondo = backPos[0] - diamBkg / 2;
				yFondo = backPos[1] - diamBkg / 2;

				//
				// disegno RoiFondo su imp1
				//

				ImageStatistics statBkg = ImageUtils.backCalc2((int) xFondo, (int) yFondo,
						MyConst.P11_DIAM_ROI_BACKGROUND, imp1, step, false, test);

				// MyLog.waitHere("Roi Fondo coordinate: x= " + xFondo + " y= "
				// + yFondo + " statFondo.mean= " + statFondo.mean);

				// over2.addElement(imp1.getRoi());
				// over2.setStrokeColor(color2);

				//
				// =============PROVVISORIO=====================================

				if (verbose) {
					ImageUtils.addOverlayRoi(imp1, Color.yellow, 1);
				}

				// over2.addElement(imp1.getRoi());
				// over2.setStrokeColor(color2);
				// if (!silent)
				imp1.updateAndDraw();
				ImagePlus imp8 = imp1.flatten();
				String newName = path1 + "_flat_p11.jpg";
				new FileSaver(imp8).saveAsJpeg(newName);

				// MyLog.appendLog(fileDir + "MyLog.txt", "saved: " + newName);
				// =============================================================

				//
				// disegno MROI su imaDiff
				//
				ImagePlus imaDiff = UtilAyv.genImaDifference(imp1, imp2);
				// ImagePlus imaDiff = UtilAyv.diffIma(imp1, imp2);
				if (!silent || (verbose && !fast)) {
					UtilAyv.showImageMaximized(imaDiff);
					ImageUtils.imageToFront(imaDiff);
				}

				imaDiff.resetDisplayRange();
				imaDiff.setOverlay(over3);
				over3.setStrokeColor(color2);

				imaDiff.setRoi(xCenterRoi - sq7 / 2, yCenterRoi - sq7 / 2, sq7, sq7);
				ImageUtils.addOverlayRoi(imaDiff, Color.green, 1);

				imaDiff.updateAndDraw();

				ImageStatistics statImaDiff = imaDiff.getStatistics();
				imaDiff.updateAndDraw();

				if (imaDiff.isVisible())
					imaDiff.getWindow().toFront();

				if (test)
					MyLog.waitHere("disegnata Mroi su immagine differenza", debug, timeout);

				if (step)
					msgMroi();
				//
				// calcolo P su imaDiff
				//
				double prelimImageNoiseEstimate_MROI = statImaDiff.stdDev / Math.sqrt(2);

				if (step) {
					msgNea(prelimImageNoiseEstimate_MROI);
				}
				//
				// loop di calcolo NEA su imp1
				//

				if (imp1.isVisible())
					ImageUtils.imageToFront(imp1);
				if (imaDiff.isVisible())
					imaDiff.hide();

				imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA, sqNEA);
				if (!silent)
					imp1.updateAndDraw();

				// MyLog.waitHere();

				imp1.setOverlay(over2);
				ImageUtils.addOverlayRoi(imp1, Color.green, 1.1);
				if (!silent)
					imp1.updateAndDraw();

				//
				// qui, se il numero dei pixel < 121 dovra' incrementare sqR2 e
				// ripetere il loop
				//
				double checkPixels = MyConst.P11_CHECK_PIXEL_MULTIPLICATOR * prelimImageNoiseEstimate_MROI;
				int area11x11 = MyConst.P11_NEA_11X11_PIXEL * MyConst.P11_NEA_11X11_PIXEL;
				int enlarge = 0;
				int pixx = 0;
				int loop = 0;
				boolean alreadyWarned = false;

				do {

					boolean paintPixels = false;

					pixx = countPixTest(imp1, xCenterRoi, yCenterRoi, sqNEA, checkPixels, paintPixels);

					imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA, sqNEA);
					if (!silent && !fast) {

						imp1.updateAndDraw();

						imp1.getRoi().setStrokeColor(Color.green);
						imp1.getRoi().setStrokeWidth(1);
						over2.addElement(imp1.getRoi());
						MyLog.waitHere("pixels utilizzabili= " + pixx, debug, timeout);
					}

					// imp1.getWindow().toFront();
					if (step)
						msgDisplayNEA();

					if (pixx < area11x11) {

						if (!confirmed) {

							imp1.deleteRoi();
							overlayGrid(imp1, MyConst.P11_GRID_NUMBER, true);
							over2.clear();
							imp1.setOverlay(over2);

							xStartRefLine2 = 0;
							yStartRefLine2 = yCenterRoi;
							xEndRefLine2 = width;
							yEndRefLine2 = yCenterRoi;

							imp1.setRoi(new Line(xStartRefLine2, yStartRefLine2, xEndRefLine2, yEndRefLine2));
							imp1.getRoi().setStrokeColor(Color.green);
							imp1.getRoi().setStrokeWidth(0.5);
							over2.add(imp1.getRoi());

							xStartRefLine2 = xCenterRoi;
							yStartRefLine2 = 0;
							xEndRefLine2 = xCenterRoi;
							yEndRefLine2 = height;

							imp1.setRoi(new Line(xStartRefLine2, yStartRefLine2, xEndRefLine2, yEndRefLine2));
							imp1.getRoi().setStrokeColor(Color.green);
							imp1.getRoi().setStrokeWidth(0.5);
							over2.add(imp1.getRoi());

							// ImageUtils.addOverlayRoi(imp1, Color.green, 1);
							// MyLog.waitHere();

							imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA, sqNEA);
							imp1.getRoi().setStrokeColor(Color.red);
							imp1.getRoi().setStrokeWidth(1.1);
//							MyLog.waitHere(info10 + "\nimp1= " + imp1.getTitle() + "\nimp2= " + imp2.getTitle()
//									+ "\n \nRichiesto riposizionamento della ROI indicata in rosso,\nORA e' possibile spostarla, oppure lasciarla dove si trova.\nPOI premere OK");

							int ko1 = 0;

//							 ko1 = MyLog.waitHereModeless(info10 + "\nimp1= " + imp1.getTitle() + "\nimp2= "
//									+ imp2.getTitle()
//									+ "\n \nRichiesto riposizionamento della ROI indicata in rosso,\nORA e' possibile spostarla, oppure lasciarla dove si trova."
//									+ "POI premere  OK, altrimenti, se l'immagine NON E'ACCETTABILE premere ANNULLA per passare alle successive");

//							int resp = ButtonMessages.ModelessMsg((info10 + "\nimp1= " + imp1.getTitle() + "\nimp2= "
//									+ imp2.getTitle() + "\n \nRichiesto riposizionamento della ROI indicata in rosso,"
//									+ "\nORA e' possibile spostarla, oppure lasciarla dove si trova.\n"
//									+ "POI premere  OK, altrimenti, se l'immagine NON E'ACCETTABILE premere <ANNULLA>"
//									+ " per passare alle successive"), "OK", "<ANNULLA>");
							
							boolean resp = MyLog.waitHereModeless("<<  SELEZIONE MANUALE ATTIVA >>\n \nimmagine= " + imp11.getTitle()
							+ "\n\nRichiesto riposizionamento della ROI quadrata indicata in rosso, dentro al fantoccio\n"
									+"\nORA e' possibile spostarla, oppure lasciarla dove si trova."
									+ "\n--- POI premere OK ---"
									+ "\nAltrimenti, se l'immagine NON FOSSE UTILIZZABILE premere <ANNULLA> per passare alle successive\n \n");

							
							
							
							
							if (resp) {
//								MyLog.waitHere("premuto annulla");
								return null;
							}

							confirmed = true;
							Rectangle boundRec4 = imp1.getProcessor().getRoi();
							xCenterRoi = (int) boundRec4.getCenterX();
							yCenterRoi = (int) boundRec4.getCenterY();
							imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA, sqNEA);

							imp1.getRoi().setStrokeWidth(1);
							imp1.getRoi().setStrokeColor(Color.green);
							imp1.updateAndDraw();
						}
						// over2.clear();
						if (!alreadyWarned)

							MyLog.waitHere(info10 + "\nimp1= " + imp1.getTitle() + "\nimp2= " + imp2.getTitle()
									+ "\n \nAccrescimento MROI in corso", debug);

						alreadyWarned = true;
						sqNEA = sqNEA + 2; // accrescimento area
						enlarge = enlarge + 1;

						// MyLog.waitHere("verificare immagine, insolito
						// accrescimento richiesto");
					}
					if (step) {
						msgEnlargeRoi(sqNEA);
					}

					// verifico che quando cresce il lato del quadrato non si
					// esca
					// dall'immagine

					if ((xCenterRoi + sqNEA - enlarge) >= width || (xCenterRoi - enlarge) <= 0) {

						msgNot121();
						return null;
					}
					if ((yCenterRoi + sqNEA - enlarge) >= height || (yCenterRoi - enlarge) <= 0) {
						msgNot121();
						return null;
					}
					if (step && pixx >= area11x11) {
						msgSqr2OK(pixx);
					}

					loop++;

				} while (pixx < area11x11);

				imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA, sqNEA);

				imp1.getRoi().setStrokeWidth(1.1);

				imp1.updateAndDraw();
				// MyLog.waitHere();

				if (imp1.isVisible())
					ImageUtils.imageToFront(imp1);
				//
				// calcolo SD su imaDiff quando i corrispondenti pixel
				// di imp1 passano il test
				//

				double[] out1 = devStandardNema(imp1, imaDiff, xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA,
						checkPixels, true, imp1.getOverlay());
				if (step)
					msgDisplayMean4(out1[0], out1[1]);

				//
				// calcolo SNR finale
				//
				double snr = signal1 / (out1[1] / Math.sqrt(2));

				// ************************ 140812*************

				// IJ.log("" + imp1.getTitle() + " signal1= " + signal1
				// + " noise1= " + out1[1] + " snr= " + snr);
				// MyLog.waitHere();

				// ********************************************

				if (step)
					msgSnr(snr);

				//
				// calcolo simulata
				//

				String patName = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PATIENT_NAME);

				String codice1 = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SERIES_DESCRIPTION);

				String codice = UtilAyv.getFiveLetters(codice1);

				// String codice = ReadDicom
				// .readDicomParameter(imp1,
				// MyConst.DICOM_SERIES_DESCRIPTION)
				// .substring(0, 4).trim();

				simpath = fileDir + "SIMULATE";
				File newdir3 = new File(simpath);
				boolean ok3 = false;
				boolean ok4 = false;
				if (newdir3.exists()) {
//					ok3 = InputOutput.deleteDir(newdir3);
//					if (!ok3)
//						MyLog.waitHere("errore cancellazione directory " + newdir3);
				} else {
					ok4 = InputOutput.createDir(newdir3);
					if (!ok4)
						MyLog.waitHere("errore creazione directory " + newdir3);
				}

				simulataName = simpath + "\\" + patName + codice + "sim.zip";

				boolean visualizza = ((verbose || test) && !fast);

				int[][] classiSimulata = ImageUtils.generaSimulata12classi(xCenterRoi, yCenterRoi, sq7, imp1,
						simulataName, step, visualizza, test);

				if (!silent && !fast)
					MyLog.waitHere("Generazione simulata 12 classi", debug, timeout);

				//
				// calcolo posizione fwhm a meta' della MROI
				//
				if (imp1.isVisible())
					ImageUtils.imageToFront(imp1);

				int xStartProfile = 0;
				int yStartProfile = 0;
				int xEndProfile = 0;
				int yEndProfile = 0;

				if (direzione < 3) {
					xStartProfile = xCenterRoi;
					yStartProfile = 1;
					xEndProfile = xStartProfile;
					yEndProfile = height;
				} else {
					xStartProfile = 1;
					yStartProfile = yCenterRoi;
					xEndProfile = width;
					yEndProfile = yStartProfile;
				}

				double[] outFwhm2 = analyzeProfile(imp1, xStartProfile, yStartProfile, xEndProfile, yEndProfile,
						dimPixel, step);

				IJ.wait(MyConst.TEMPO_VISUALIZZ);

				//
				// Salvataggio dei risultati nella ResultsTable

				// String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);
				TableCode tc1 = new TableCode();
				String[][] tabCodici = tc1.loadMultipleTable("codici", ".csv");

				String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp1, tabCodici,
						VERSION + "_P11__ContMensili_" + MyVersion.CURRENT_VERSION + "__iw2ayv_"
								+ MyVersionUtils.CURRENT_VERSION,
						autoCalled);

				//

				rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);
				rt.showRowNumbers(true);

				int col = 2;
				String t1 = "TESTO";
				String s2 = "VALORE";
				String s3 = "roi_x";
				String s4 = "roi_y";
				String s5 = "roi_b";
				String s6 = "roi_h";

				rt.addValue(t1, simulataName);
				rt.incrementCounter();

				rt.addValue(t1, "Segnale");
				rt.addValue(s2, signal1);
				rt.addValue(s3, xCenterRoi);
				rt.addValue(s4, yCenterRoi);
				rt.addValue(s5, sqNEA);
				rt.addValue(s6, sqNEA);

				rt.incrementCounter();
				rt.addValue(t1, "Rumore_Fondo");

				// =================================================
				// ERRATO, QUI BISOGNA STAMPARE OUT1[1] ANZICHE'
				// STATFONDO.MEAN
				// rt.addValue(2, statFondo.mean);
				rt.addValue(s2, (out1[1] / Math.sqrt(2)));
				// =================================================

				int xRoi = (int) statBkg.roiX;
				int yRoi = (int) statBkg.roiY;
				int widthRoi = (int) statBkg.roiWidth;
				int heightRoi = (int) statBkg.roiHeight;

				rt.addValue(s3, xRoi);
				rt.addValue(s4, yRoi);
				rt.addValue(s5, widthRoi);
				rt.addValue(s6, heightRoi);

				rt.incrementCounter();
				rt.addValue(t1, "SnR");
				rt.addValue(s2, snr);
				rt.addValue(s3, xCenterRoi);
				rt.addValue(s4, yCenterRoi);
				rt.addValue(s5, sqNEA);
				rt.addValue(s6, sqNEA);

				rt.incrementCounter();
				rt.addValue(t1, "FWHM");
				rt.addValue(s2, outFwhm2[0]);
				rt.addValue(s3, xStartProfile);
				rt.addValue(s4, yStartProfile);
				rt.addValue(s5, xEndProfile);
				rt.addValue(s6, yEndProfile);

				rt.incrementCounter();
				rt.addValue(t1, "Bkg");
				rt.addValue(s2, statBkg.mean);
				rt.addValue(s3, statBkg.roiX);
				rt.addValue(s4, statBkg.roiY);
				rt.addValue(s5, statBkg.roiWidth);
				rt.addValue(s6, statBkg.roiHeight);

				String[] levelString = { "+20%", "+10%", "-10%", "-20%", "-30%", "-40%", "-50%", "-60%", "-70%", "-80%",
						"-90%", "fondo" };

				for (int i1 = 0; i1 < classiSimulata.length; i1++) {
					rt.incrementCounter();
					rt.addValue(t1, ("Classe" + classiSimulata[i1][0]) + "_" + levelString[i1]);
					rt.addValue(s2, classiSimulata[i1][1]);
				}

				// vado a forzare in riga1 i nomi dei file immagini utilizzate
				rt.setValue(1, 0, imp1.getShortTitle());
				rt.setValue(2, 0, imp2.getShortTitle());

				if (verbose && !test && !fast) {
					rt.show("Results");
				}

				if (fast || autoCalled || silent) {
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
			}

		} while (!accetta || manualRequired2);
		return rt;

	}

	/**
	 * Self test execution menu
	 */
	public void selfTestMenu(boolean verbose, int timeout) {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			boolean ok = false;
			switch (userSelection2) {
			case 1:
				// GE
				ok = selfTestGe(verbose, timeout);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				break;

			case 2:
				ok = selfTestSiemens(verbose, timeout);
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

		// 0.0, 1275.530612244898, 37.045823571316845, 34.43115820571181,
		// 38.891139825926565, 7.67, 2616.0, 505.0, 1163.0, 661.0, 764.0, 910.0,
		// 1094.0, 1382.0, 1799.0, 2752.0, 5478.0, 46412.0,

		// 0.0, 953.6122448979592, 3.271011755762441, 291.53433741655306,
		// 51.16454748650474, 3.46, 573.0, 419.0, 1085.0, 689.0, 820.0, 1082.0,
		// 1386.0, 1890.0, 2681.0, 3981.0, 5089.0, 45841.0,

		// SIGNAL ERRATO 131.1526336669922 anziche' 953.6122448979592
		// SNRATIO ERRATO 40.09543329703558 anziche' 291.53433741655306

		double simul = 0.0;
		double signal = 953.6122448979592;
		// double backNoise = 7.85;
		double backNoise = 3.271011755762441;
		double snRatio = 291.53433741655306;
		double fwhm = 51.16454748650474;
		double bkg = 3.46;
		double num1 = 573.0;
		double num2 = 419.0;
		double num3 = 1085.0;
		double num4 = 689.0;
		double num5 = 820.0;
		double num6 = 1082.0;
		double num7 = 1386.0;
		double num8 = 1890.0;
		double num9 = 2681.0;
		double num10 = 3981.0;
		double num11 = 5089.0;
		double num12 = 45841.0;

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm, bkg, num1, num2, num3, num4, num5, num6,
				num7, num8, num9, num10, num11, num12 };
		return vetReference;

	}

	/**
	 * Ge test image expected results
	 * 
	 * @return
	 */
	public static double[] referenceGe() {

		// / 0.0, 2278.4897959183672, 24.209007271217278, 94.11744027303934,
		// 36.25918394251357, 0.34, 1393.0, 430.0, 1161.0, 816.0, 1159.0,
		// 1556.0, 2726.0, 3614.0, 2588.0, 2644.0, 1549.0, 45900.0,

		double simul = 0.0;
		double signal = 2278.4897959183672;
		double backNoise = 24.209007271217278;
		double snRatio = 94.11744027303934;
		double fwhm = 36.25918394251357;
		double bkg = 0.34;
		double num1 = 1393.0;
		double num2 = 430.0;
		double num3 = 1161.0;
		double num4 = 816.0;
		double num5 = 1159.0;
		double num6 = 1556.0;
		double num7 = 2726.0;
		double num8 = 3614.0;
		double num9 = 2588.0;
		double num10 = 2644.0;
		double num11 = 1549.0;
		double num12 = 45900.0;

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm, bkg, num1, num2, num3, num4, num5, num6,
				num7, num8, num9, num10, num11, num12 };
		return vetReference;
	}

	public static boolean selfTestGe(boolean verbose, int timeout) {
		String[] list = { "CTSA2_01testP11", "CTSA2_03testP11", "CTSA2_02testP11" };
		String[] path = new InputOutput().findListTestImages2(MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path[0];
		String path2 = path[2];
		// String path3 = path[1];
		// boolean autoCalled = false;
		// boolean step = false;
		// boolean verbose = true;
		// boolean test = true;
		double[] vetReference = referenceGe();
		int verticalDir = 3;
		double profond = 30.0;
		// boolean fast = true;
		// boolean silent = false;

		int mode;
		if (verbose)
			mode = 10; // modalita' demo
		else
			mode = 0; // modalita' silent

		ResultsTable rt1 = mainUnifor(path1, path2, verticalDir, profond, "", mode, timeout);
		if (rt1 == null) {
			MyLog.waitHere("rt1==null");
			return false;
		}
		double[] vetResults = UtilAyv.vectorizeResultsNew(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P11_vetName);
		return ok;
	}

	public static boolean selfTestSiemens(boolean verbose, int timeout) {
		// String path1 = "./Test2/S12S_01testP11";
		// String path2 = "./Test2/S12S_02testP11";

		// String[] list = { "S1SA_01testP5", "S1SA_02testP5", "S1SA_03testP5"
		// };
		String[] list = { "S12S_01testP11", "S12S_02testP11" };
		String[] path = new InputOutput().findListTestImages2(MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path[0];
		// String path3 = path[2];
		String path2 = path[1];
		// boolean autoCalled = false;
		// boolean step = false;
		// boolean test = true;
		// boolean fast = true;
		// boolean silent = !verbose;

		int mode;
		if (verbose)
			mode = 10; // modalita' demo
		else
			mode = 0; // modalita' silent

		double[] vetReference = referenceSiemens();
		int verticalDir = 3;
		double profond = 30.0;
		ResultsTable rt1 = mainUnifor(path1, path2, verticalDir, profond, "", mode, timeout);
		if (rt1 == null)
			return false;
		double[] vetResults = UtilAyv.vectorizeResultsNew(rt1);
		// MyLog.logVector(vetResults, "vetResults");
		// MyLog.logVector(vetReference, "vetReference");
		// MyLog.waitHere();
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P11_vetName);
		return ok;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {
		boolean verbose = false;
		int timeout = 0;
		boolean ok = selfTestSiemens(verbose, timeout);

		if (ok) {
			IJ.log("Il test di p11rmn_ UNIFORMITA' SUPERFICIALE e' stato SUPERATO");
		} else {
			IJ.log("Il test di p11rmn_ UNIFORMITA' SUPERFICIALE evidenzia degli ERRORI");
		}

		return;

	}

	/**
	 * Calcola la deviazione standard
	 * 
	 * @param num  Numero dei pixel
	 * @param sum  Somma dei valori pixel
	 * @param sum2 Somma dei quadrati dei valori dei pixel
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
	 * @param imp1        immagine in input
	 * @param sqX         coordinata della Roi
	 * @param sqY         coordinata della Roi
	 * @param sqR         lato della Roi
	 * @param limit       soglia di conteggio
	 * @param paintPixels switch per test
	 * @return pixel che superano la soglia
	 */
	public static int countPixTest(ImagePlus imp1, int sqX, int sqY, int sqR, double limit, boolean paintPixels) {
		int offset = 0;
		int w = 0;
		int count1 = 0;
		short[] pixels2 = null;

		if (imp1 == null) {
			IJ.error("CountPixTest ricevuto null");
			return (0);
		}

		int width = imp1.getWidth();
		short[] pixels1 = UtilAyv.truePixels(imp1);
		ImageProcessor ip1 = imp1.getProcessor();
		if (paintPixels) {
			// N.B. la roi qui sotto e' volutamente centrata senza togliere
			// lato/2
			imp1.setRoi(sqX, sqY, sqR, sqR);
			imp1.updateAndDraw();
			pixels2 = (short[]) ip1.getPixels();
		}

		for (int y1 = sqY - sqR / 2; y1 <= (sqY + sqR / 2); y1++) {
			offset = y1 * width;
			for (int x1 = sqX - sqR / 2; x1 <= (sqX + sqR / 2); x1++) {
				w = offset + x1;
				if (w > 0 && pixels1[w] > limit) {
					if (paintPixels)
						pixels2[w] = 4096;
					count1++;
				}
			}
		}
		imp1.updateAndDraw();
		return count1;

	}

	/**
	 * Effettua il calcolo della deviazione standard per i pixel della immagine
	 * differenza i cui corrispondenti pixel della prima immagine oltrepassano la
	 * soglia di conteggio, secondo il protocollo NEMA
	 * 
	 * @param imp1  immagine in input
	 * @param imp3  immagine differenza
	 * @param sqX   coordinata della Roi
	 * @param sqY   coordinata della Roi
	 * @param sqR   lato della Roi
	 * @param limit soglia di conteggio
	 * @return [0] sum / pixelcount [1] devStan
	 */

	public static double[] devStandardNema(ImagePlus imp1, ImagePlus imp3, int sqX, int sqY, int sqR, double limit,
			boolean paintPixels, Overlay over1) {
		double[] results = new double[2];
		double value4 = 0.0;
		double sumValues = 0.0;
		double sumSquare = 0.0;
		boolean ok;

		if ((imp1 == null) || (imp3 == null)) {
			IJ.error("devStandardNema ricevuto imp null");
			return (null);
		}
		int width = imp1.getWidth();
		short[] pixels1 = UtilAyv.truePixels(imp1);
		int pixelCount = 0;
		int offset = 0;
		ImageProcessor ip3 = imp3.getProcessor();
		float[] pixels4 = (float[]) ip3.getPixels();
		for (int y1 = sqY; y1 < (sqY + sqR); y1++) {
			ok = false;
			for (int x1 = sqX; x1 < (sqX + sqR); x1++) {
				offset = y1 * width + x1;
				ok = true;
				if (pixels1[offset] > limit) {
					pixelCount++;
					value4 = pixels4[offset];
					sumValues += value4;
					sumSquare += value4 * value4;
					// modifica del 200117
					if (paintPixels)
						setOverlayPixel(over1, imp1, x1, y1, Color.yellow, Color.red, ok);
					// --------

				}
			}
		}
		results[0] = sumValues / pixelCount;
		double sd1 = calculateStdDev4(pixelCount, sumValues, sumSquare);
		results[1] = sd1;
		return (results);
	}

	public static void setOverlayPixel(Overlay over1, ImagePlus imp1, int x1, int y1, Color col1, Color col2,
			boolean ok) {
		imp1.setRoi(x1, y1, 1, 1);
		if (ok) {
			imp1.getRoi().setStrokeColor(col1);
			imp1.getRoi().setFillColor(col1);
		} else {
			imp1.getRoi().setStrokeColor(col1);
			imp1.getRoi().setFillColor(col2);
		}
		over1.addElement(imp1.getRoi());
		imp1.deleteRoi();
	}

	/**
	 * Analisi di un profilo NON mediato
	 * 
	 * @param imp1 Immagine da analizzare
	 * @param ax   Coordinata x inizio segmento
	 * @param ay   Coordinata y inizio segmento
	 * @param bx   Coordinata x fine segmento
	 * @param by   Coordinata x fine segmento
	 * 
	 * @return outFwhm[0]=FWHM, outFwhm[1]=peak position
	 */

	private static double[] analyzeProfile(ImagePlus imp1, int ax, int ay, int bx, int by, double dimPixel,
			boolean step) {

		if (imp1 == null) {
			IJ.error("analProf2 ricevuto null");
			return (null);
		}

		imp1.setRoi(new Line((int) ax, (int) ay, (int) bx, (int) by));
		Roi roi1 = imp1.getRoi();

		double[] profi1 = ((Line) roi1).getPixels(); // profilo non mediato
		profi1[profi1.length - 1] = 0; // azzero a mano l'ultimo pixel

		if (step) {
			imp1.updateAndDraw();
			ButtonMessages.ModelessMsg("Profilo non mediato  <50>", "CONTINUA");
		}

		int vetHalfPoint[];
		double[] outFwhm;
		vetHalfPoint = MyFwhm.halfPointSearch(profi1);
		outFwhm = MyFwhm.calcFwhm(vetHalfPoint, profi1, dimPixel, "FWHM", false);
		if (step)

			createPlotP11(profi1, true, true); // plot della fwhm
		if (step)
			ButtonMessages.ModelessMsg("Continuare?   <51>", "CONTINUA");
		imp1.deleteRoi();
		return (outFwhm);
	} // analProf2

	/**
	 * Calcolo dell'FWHM su di un vettore profilo
	 * 
	 * @param vetUpDwPoints Vettore restituito da AnalPlot2 con le posizioni dei
	 *                      punti sopra e sotto la meta' altezza
	 * @param profile       Profilo da analizzare
	 * @return out[0]=FWHM, out[1]=peak position
	 */

	// private static double[] calcFwhm(int[] vetUpDwPoints, double profile[],
	// double dimPixel) {
	//
	// // MyLog.logVector(vetUpDwPoints, "vetUpDwPoints");
	//
	// double peak = 0;
	// double[] a = Tools.getMinMax(profile);
	// double min = a[0];
	// double max = a[1];
	// // interpolazione lineare sinistra
	// double px0 = vetUpDwPoints[0];
	// double px1 = vetUpDwPoints[1];
	// double px2;
	// double py0 = profile[vetUpDwPoints[0]];
	// double py1 = profile[vetUpDwPoints[1]];
	// double py2 = (max - min) / 2.0 + min;
	// px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
	// double sx = px2;
	//
	// // interpolazione lineare destra
	// px0 = vetUpDwPoints[2];
	// px1 = vetUpDwPoints[3];
	// py0 = profile[vetUpDwPoints[2]];
	// py1 = profile[vetUpDwPoints[3]];
	// py2 = (max - min) / 2.0 + min;
	// px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
	// double dx = px2;
	//
	// double fwhm = (dx - sx) * dimPixel; // in mm
	// for (int i1 = 0; i1 < profile.length; i1++) {
	// if (profile[i1] == min)
	// peak = i1;
	// }
	//
	// double[] out = { fwhm, peak };
	// return (out);
	// } // calcFwhm

	/**
	 * Mostra a video un profilo con linea a meta' picco
	 * 
	 * @param profile1 Vettore con il profilo da analizzare
	 * @param bslab    Flag slab che qui mettiamo sempre true
	 */

	private static void createPlotP11(double profile1[], boolean bslab, boolean bLabelSx) {

		int[] vetUpDwPoints = MyFwhm.halfPointSearch(profile1);
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
		Plot plot = new Plot("Plot profilo penetrazione", "pixel", "valore", xcoord1, profile1);
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
		plot.addLabel(labelPosition, 0.50,
				"down sx " + vetUpDwPoints[0] + "  =   " + IJ.d2s(profile1[vetUpDwPoints[0]], 2));
		plot.addLabel(labelPosition, 0.55,
				"down dx " + vetUpDwPoints[2] + "  =   " + IJ.d2s(profile1[vetUpDwPoints[2]], 2));
		plot.addLabel(labelPosition, 0.60,
				"up      sx " + vetUpDwPoints[1] + "  =   " + IJ.d2s(profile1[vetUpDwPoints[1]], 2));
		plot.addLabel(labelPosition, 0.65,
				"up      dx " + vetUpDwPoints[3] + "  =   " + IJ.d2s(profile1[vetUpDwPoints[3]], 2));
		plot.addLabel(labelPosition, 0.70, "sx interp       =  " + IJ.d2s(sx, 2));
		plot.addLabel(labelPosition, 0.75, "dx interp       =  " + IJ.d2s(dx, 2));
		plot.addLabel(labelPosition, 0.80, "fwhm            =  " + IJ.d2s(fwhm, 2));
		plot.setColor(color2);
		xVetLineHalf[0] = 0;
		xVetLineHalf[1] = len1;
		yVetLineHalf[0] = half;
		yVetLineHalf[1] = half;
		plot.addPoints(xVetLineHalf, yVetLineHalf, PlotWindow.LINE);
		plot.setColor(color1);
		plot.show();

		plot.draw();
	} // createPlot2

	public static void overlayGrid(ImagePlus imp1, int gridNumber, boolean verbose) {

		CustomCanvasGeneric ccg1 = new CustomCanvasGeneric(imp1);
		ccg1.setGridElements(gridNumber);
		if (verbose) {
			ImageWindow iw1 = new ImageWindow(imp1, ccg1);
			iw1.maximize();
		}
	}

	private static void msgNot121() {
		IJ.showMessage("ATTENZIONE la NEA esce dall'immagine\nsenza" + " riuscire a trovare 121 pixel che superino il"
				+ " test\nil programma TERMINA PREMATURAMENTE");
	}

	private static void msgMroi() {
		ButtonMessages.ModelessMsg("Disegnata Mroi su imaDiff", "CONTINUA");
	}

	private static void msgNea(double noise) {
		ButtonMessages.ModelessMsg("P=" + noise + "  (Preliminary Noise Estimate MROI ima4)", "CONTINUA");
	}

	private static void msgEnlargeRoi(int sqNEA) {
		ButtonMessages.ModelessMsg("Accrescimento MROI lato=" + sqNEA, "CONTINUA");
	}

	private static void msgSqr2OK(int pixx) {
		ButtonMessages.ModelessMsg("sqR2 OK  poiche' pixx=" + pixx, "CONTINUA");
	}

	private static void msgSnr(double snr) {
		ButtonMessages.ModelessMsg("SNR finale=" + snr, "CONTINUA");
	}

	private static void msgDisplayMean4(double mean, double stdDev) {
		ButtonMessages.ModelessMsg("mean4= " + mean + " standard_deviation4= " + stdDev, "CONTINUA");
	}

	private static void msgDisplayNEA() {
		ButtonMessages.ModelessMsg("displayNEA", "CONTINUA");
	}

	private static int menuPositionMroi() {
		int userSelection1 = ButtonMessages.ModelessMsg(
				"Posizionare la MROI sull'area della bobina" + "  e premere Accetta", "ACCETTA", "RIDISEGNA");
		return userSelection1;
	}

	/***
	 * Legge la direzione preimpostata nel file di configurazione (codici)
	 * 
	 * @param in1
	 * @return codice numerico direzione
	 */
	public static int decodeDirezione(String in1) {
		int out = 0;

		if (in1.compareToIgnoreCase("x") == 0)
			out = 0;

		else if (in1.compareToIgnoreCase("vup") == 0)
			out = 1;
		else if (in1.compareToIgnoreCase("vdw") == 0)
			out = 2;
		else if (in1.compareToIgnoreCase("hsx") == 0)
			out = 3;
		else if (in1.compareToIgnoreCase("hdx") == 0)
			out = 4;
		else {
			MyLog.waitHere("Errore nella direzione in " + MyConst.CODE_FILE + " valore " + in1 + " non previsto");
			out = -1;
		}

		return out;
	}

	/**
	 * 
	 * @param imp11
	 * @param autoCalled
	 * @param direzioneTabella 1=verticale a salire, 2=verticale a scendere,
	 *                         3=orizzontale sinistra, 4=orizzontale destra
	 * @param profond
	 * @param info10
	 * @param mode
	 * @param timeout
	 * @return
	 */
	public static double[] positionSearch(ImagePlus imp11, boolean autoCalled, int direzioneTabella, double profond,
			String info10, int mode, int timeout) {
		//
		// ================================================================================
		// Inizio calcoli geometrici
		// ================================================================================
		//
		boolean verbose = false;
		boolean silent = false;
		boolean step = false;
		boolean test = false;
		boolean fast = false;

		switch (mode) {
		case 0:
			silent = true;
			break;
		case 1:
			autoCalled = true;
			fast = true;
			break;
		case 2:
			verbose = true;
			step = true;
			break;
		case 3:
			verbose = true;
			step = true;
			break;
		case 4:
			verbose = true;
			step = true;
			break;
		case 5:
			verbose = true;
			step = true;
			break;
		case 10:
			verbose = true;
			test = true;
			break;
		}

		int direzione = 0;
		int who1 = 0;
		boolean manualRequired = false;

		double dimPixel = ReadDicom.readDouble(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp11, MyConst.DICOM_PIXEL_SPACING), 1));

		int width = imp11.getWidth();
		int height = imp11.getHeight();

		imp11.updateAndDraw();

		if (verbose)
			imp11.setTitle(
					"DIMENSIONI RETICOLO= " + (dimPixel * (double) height / (double) MyConst.P11_GRID_NUMBER) + " mm");

		Overlay over11 = new Overlay();
		imp11.setOverlay(over11);
		imp11.updateAndDraw();

//		double[] out11 = MyFilter.maxPosition1x1(imp11);
//		double xMaximum1 = out11[0];
//		double yMaximum1 = out11[1];
		double[] out1 = MyFilter.maxPosition7x7(imp11);
		double xMaximum = out1[0];
		double yMaximum = out1[1];

//		if ((xMaximum1-xMaximum)>7 || (yMaximum1-yMaximum)>7){
//			MyLog.waitHere("CACATA in arrivo!");
//			
//		}

		direzione = directionFinder(imp11, xMaximum, yMaximum, silent, timeout);

		if (direzione != 0 && direzione != direzioneTabella) {
			// MyLog.waitHere("Rilevata differenza tra directionFinder e
			// direzioneTabella direzione= " + direzione
			// + " direzioneTabella= " + direzioneTabella);
			manualRequired = true;
			who1 = 1;
			direzione = direzioneTabella;

		}

		if (direzione == 0) {
			// manualRequired = true;
			// who1= 2;
			// se non riesco a decidere, lascio fare alla tabella che e' (forse)
			// piu' saggia di me
			direzione = direzioneTabella;
			// manualRequired = true;
			// MyLog.waitHere("per direzione=0 adotto direzioneTabella= "
			// + direzioneTabella);
		}

		// if (fast && (step || test)) {
		imp11.setRoi(new OvalRoi((int) xMaximum - 4, (int) yMaximum - 4, 8, 8));
		over11.addElement(imp11.getRoi());
		over11.setStrokeColor(color1);
		imp11.updateAndDraw();
		if (step)
			MyLog.waitHere("Maximum value= " + out1[2] + " find at x= " + xMaximum + " y= " + yMaximum, debug, timeout);
		// }
		if (test)
			MyLog.waitHere("Maximum position", debug, timeout);

		// IJ.log("width= " + width + " height= " + height);

		double startX = Double.NaN;
		double startY = Double.NaN;
		double endX = Double.NaN;
		double endY = Double.NaN;

		String strDirez = "";
		double ax = Double.NaN;
		double ay = Double.NaN;

		// MyLog.waitHere("direzione= " + direzione);
		// vup = 1 vdw = 2 hsx = 3 hdx = 4
		switch (direzione) {
		case 1:
			strDirez = " verticale a salire";
			startX = 0;
			endX = width;
			startY = out1[1] - profond / dimPixel;
			if (startY < 0) {
				// MyLog.waitHere("Imposto manualRequired"); // commentato per
				// bottiglia verticale
				// manualRequired = true;
				who1 = 3;
				startY = startY + height;
			}
			endY = startY;
			break;
		case 2:
			strDirez = " verticale a scendere";
			startX = 0;
			endX = width;
			startY = out1[1] + profond / dimPixel;
			if (startY > height) {
				MyLog.waitHere("Imposto manualRequired");
				manualRequired = true;
				who1 = 4;
				startY = startY - height;
			}
			endY = startY;
			break;
		case 3:
			strDirez = " orizzontale a sinistra";
			startX = out1[0] - profond / dimPixel;
			if (startX < 0) {
				MyLog.waitHere("Imposto manualRequired");
				manualRequired = true;
				who1 = 5;
				startX = startX + width;
			}

			endX = startX;
			startY = 0;
			endY = height;
			break;
		case 4:
			strDirez = " orizzontale a destra";
			startX = out1[0] + profond / dimPixel;
			if (startX > width) {
				MyLog.waitHere("Imposto manualRequired");
				manualRequired = true;
				who1 = 6;
				startX = startX - width;
			}
			endX = startX;
			startY = 0;
			endY = height;
			break;
		case 0:
			MyLog.waitHere("Caso impossibile, la direzione 0 e' gestita in precedenza");
		}

		// MyLog.waitHere("startX= " + startX + " startY= " + startY + " endX= "
		// + endX + " endY= " + endY);

		if (direzione == 0) {
			ax = width / 2;
			ay = height / 2;
		} else {
			imp11.setRoi(new Line(startX, startY, endX, endY));
			over11.addElement(imp11.getRoi());
			over11.setStrokeColor(color1);
			imp11.updateAndDraw();

			if (step || test)
				MyLog.waitHere("Selezione automatica direzione = " + strDirez, debug, timeout);

			double[] profi1 = ((Line) imp11.getRoi()).getPixels();
			profi1[0] = 0;
			profi1[profi1.length - 1] = 0;

			int[] vetHalfPoint = MyFwhm.halfPointSearch(profi1);

			double[] xPoints = new double[vetHalfPoint.length];
			double[] yPoints = new double[vetHalfPoint.length];
			for (int i1 = 0; i1 < vetHalfPoint.length; i1++) {
				xPoints[i1] = (double) vetHalfPoint[i1];
				yPoints[i1] = profi1[vetHalfPoint[i1]];
			}

			double[] fwhm = MyFwhm.calcFwhm(vetHalfPoint, profi1, dimPixel, "", false);
			double centro = vetHalfPoint[0] + (fwhm[0] / 2) / dimPixel;

			double xCenter[] = { centro };
			double yCenter[] = { profi1[(int) centro] };

			if (step || test) {
				Plot plot1 = MyPlot.basePlot(profi1, "PROFILO SEGNALE LUNGO LINEA VERDE", Color.blue);
				plot1.draw();
				plot1.setColor(Color.red);
				plot1.addPoints(xPoints, yPoints, PlotWindow.CIRCLE);
				plot1.draw();
				plot1.addPoints(xCenter, yCenter, PlotWindow.BOX);
				plot1.show();
				MyLog.waitHere(listaMessaggi(2), debug, timeout);
			}

			if (direzione < 3) {
				ax = centro;
				ay = startY;

			} else {
				ax = startX;
				ay = centro;

			}
		}

		if (!fast && (step || test || manualRequired)) {
			imp11.setRoi(new OvalRoi((int) ax - 4, (int) ay - 4, 8, 8));
			over11.addElement(imp11.getRoi());
			over11.setStrokeColor(color1);
			imp11.updateAndDraw();
		}

		imp11.setRoi((int) ax - 10, (int) ay - 10, 20, 20);

		if (manualRequired) {
			imp11.deleteRoi();
			over11.clear();
			// UtilAyv.showImageMaximized(imp11);

			overlayGrid(imp11, MyConst.P11_GRID_NUMBER, true);
			imp11.setOverlay(over11);
			imp11.setRoi(new Line(0, (int) ay, imp11.getWidth(), (int) ay));
			imp11.getRoi().setStrokeColor(Color.green);
			imp11.getRoi().setStrokeWidth(.5);
			over11.addElement(imp11.getRoi());
			imp11.setRoi(new Line((int) ax, 0, (int) ax, imp11.getHeight()));
			imp11.getRoi().setStrokeColor(Color.green);
			imp11.getRoi().setStrokeWidth(.5);
			over11.addElement(imp11.getRoi());

			imp11.deleteRoi();
			imp11.updateAndDraw();

			int sqNEA = MyConst.P11_NEA_11X11_PIXEL;
			imp11.setRoi((int) ax - sqNEA / 2, (int) ay - sqNEA / 2, sqNEA, sqNEA);
			imp11.getRoi().setStrokeColor(Color.red);
			imp11.getRoi().setStrokeWidth(1.1);

			if (!imp11.isVisible())
				UtilAyv.showImageMaximized(imp11);
			imp11.updateAndDraw();
			ImageUtils.imageToFront(imp11);

			MyLog.waitHere("<< SELEZIONE MANUALE ATTIVA >>\n \n" + info10 + "\nimp11= " + imp11.getTitle()
					+ "\n \nRichiesto riposizionamento della ROI indicata in rosso,"
					+ "\nORA e' possibile spostarla, oppure lasciarla dove si trova.\nPOI premere OK");

			manualRequired = false;
			who1 = 0;

		}

		Rectangle boundRec3 = imp11.getProcessor().getRoi();
		double xCenterRoi = boundRec3.getCenterX();
		double yCenterRoi = boundRec3.getCenterY();

		double[] out = new double[8];
		out[0] = xCenterRoi;
		out[1] = yCenterRoi;
		out[2] = startX;
		out[3] = startY;
		out[4] = endX;
		out[5] = endY;
		out[6] = xMaximum;
		out[7] = yMaximum;

		if (manualRequired)
			return null;
		else
			return out;
	}

	/**
	 * Cerca la direzione in cui si trova il fantoccio La routine imposta quattro
	 * ROI attorno al punto di massima. Le medie vengono messe in un array ed
	 * ordinate. Le medie superiori od uguali all'elemento 2 sono le due maggiori.
	 * Cio' determina la direzione in cui troviamo il massimo segnale e,
	 * presumibilmente il fantoccio.
	 * 
	 * 1 -
	 * 
	 * 
	 * @param imp1
	 * @param xMaximum
	 * @param yMaximum
	 * @return
	 */
	public static int directionFinder(ImagePlus imp1, double xMaximum, double yMaximum, boolean silent, int timeout) {
		int width = imp1.getWidth();
		int height = imp1.getHeight();
		if (!silent)
			UtilAyv.showImageMaximized2(imp1);
		Overlay over1 = new Overlay();
		ImageStatistics stat1;
		imp1.setOverlay(over1);
		// marco il punto di max, per l'overlay
		imp1.setRoi(new OvalRoi((int) xMaximum - 2, (int) yMaximum - 2, 4, 4));
		imp1.getRoi().setStrokeColor(Color.red);
		over1.addElement(imp1.getRoi());
		// imposto ora quattro Roi quadrate, scegliero' le due con la media piu'
		// alta, ed in base a questo decidero' la direzione in cui muovere la
		// MROI
		double[] meanx = new double[4];

		int x1 = (int) xMaximum - 8;
		if (x1 < 0)
			x1 = x1 + width;
		if (x1 > width)
			x1 = x1 - width;

		int y1 = (int) yMaximum - 8;
		if (y1 < 0)
			y1 = y1 + height;
		if (y1 > height)
			y1 = y1 - height;

		imp1.setRoi(x1, y1, 4, 4);
		imp1.getRoi().setStrokeColor(Color.green);
		over1.addElement(imp1.getRoi());
		stat1 = imp1.getStatistics();
		double mean1 = stat1.mean;
		if (UtilAyv.isNaN(mean1))
			mean1 = 0;
		meanx[0] = mean1;

		x1 = (int) xMaximum - 8;
		if (x1 < 0)
			x1 = x1 + width;
		if (x1 > width)
			x1 = x1 - width;

		y1 = (int) yMaximum + 4;
		if (y1 < 0)
			y1 = y1 + height;
		if (y1 > height)
			y1 = y1 - height;

		imp1.setRoi(x1, y1, 4, 4);
		imp1.getRoi().setStrokeColor(Color.red);
		over1.addElement(imp1.getRoi());
		stat1 = imp1.getStatistics();
		double mean2 = stat1.mean;
		if (UtilAyv.isNaN(mean2))
			mean2 = 0;
		meanx[1] = mean2;

		x1 = (int) xMaximum + 4;
		if (x1 < 0)
			x1 = x1 + width;
		if (x1 > width)
			x1 = x1 - width;

		y1 = (int) yMaximum - 8;
		if (y1 < 0)
			y1 = y1 + height;
		if (y1 > height)
			y1 = y1 - height;

		imp1.setRoi(x1, y1, 4, 4);
		imp1.getRoi().setStrokeColor(Color.yellow);
		over1.addElement(imp1.getRoi());
		stat1 = imp1.getStatistics();
		double mean3 = stat1.mean;
		if (UtilAyv.isNaN(mean3))
			mean3 = 0;

		meanx[2] = mean3;

		x1 = (int) xMaximum + 4;
		if (x1 < 0)
			x1 = x1 + width;
		if (x1 > width)
			x1 = x1 - width;

		y1 = (int) yMaximum + 4;
		if (y1 < 0)
			y1 = y1 + height;
		if (y1 > height)
			y1 = y1 - height;

		imp1.setRoi(x1, y1, 4, 4);
		imp1.getRoi().setStrokeColor(Color.magenta);
		over1.addElement(imp1.getRoi());
		stat1 = imp1.getStatistics();
		double mean4 = stat1.mean;
		if (UtilAyv.isNaN(mean4))
			mean4 = 0;

		meanx[3] = mean4;
		// MyLog.logVector(meanx, "meanx prima di sort");

		Arrays.sort(meanx);

		// regola: mean[2] deve essere almeno > 4*mean[1], questo evita i casi
		// in cui un solo quadrato ha intercettato il fantoccio, conferma
		// manuale
		// regola: dobbiamo avere un max di solo 2 NaN o, meglio, anche se
		// abbiamo un solo NaN chiediamo la conferma manuale
		if (!(meanx[2] > 4 * meanx[1])) {
			// MyLog.logVector(meanx, "meanx");
			// MyLog.waitHere("direzione 0 per scarsa differenza");
			return 0;
		}

		// i valori da utilizzare sono >= dell'elemento3

		if (mean1 >= meanx[2] && mean3 >= meanx[2]) {
			// caso UP
			return 1;
		}
		if (mean2 >= meanx[2] && mean4 >= meanx[2]) {
			// caso DW
			return 2;
		}
		if (mean1 >= meanx[2] && mean2 >= meanx[2]) {
			// caso SX
			return 3;
		}
		if (mean3 >= meanx[2] && mean4 >= meanx[2]) {
			// caso DX
			return 4;
		}
		MyLog.logVector(meanx, "meanx");
		MyLog.waitHere("Direzione 0 perche' non entrato nelle altre");
		return 0;
	}

	/**
	 * Qui sono raggruppati tutti i messaggi del plugin, in questo modo e'
	 * facilitata la eventuale modifica / traduzione dei messaggi.
	 * 
	 * @param select
	 * @return
	 */
	public static String listaMessaggi(int select) {
		String[] lista = new String[100];
		// ---------+-----------------------------------------------------------+
		// lista[0] = "Verifica E/O modifica manuale posizione ROI";
		lista[2] = "Analizzando il segnale lungo la linea verde si calcola la FWHM \ne ne si determina il centro. Qui verra' posizionata la MROI";

		// ---------+-----------------------------------------------------------+
		String out = lista[select];
		return out;
	}

} // p11rmn_
