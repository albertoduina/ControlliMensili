package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.NewImage;
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
import ij.plugin.filter.MaximumFinder;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.CustomCanvasGeneric;
import utils.InputOutput;
import utils.Msg;
import utils.MyConst;
import utils.MyFileLogger;
import utils.MyLog;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableSequence;
import utils.TableUtils;
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
 * Analizza UNIFORMITA', SNR, FWHM per le bobine superficiali
 * 
 * Per salvare i dati in formato xls necessita di Excel_Writer.jar nella
 * directory plugins
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */
public class p11rmn_OLD implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "p11_rmn_v1.10_13oct11_";

	private String TYPE = " >> CONTROLLO SUPERFICIALI____________";

	// ---------------------------"01234567890123456789012345678901234567890"

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.xls
	 */
	private static String fileDir = "";

	private static String simulataName = "";

	// private boolean profiVert = false;

	public void run(String args) {

		fileDir = Prefs.get("prefer.string1", "none");

		if (IJ.versionLessThan("1.43k"))
			return;

		MyFileLogger.logger.info("p11rmn_rmn_>>> fileDir = " + fileDir);

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
				new AboutBox().about("Controllo Bobine Superficiali",
						this.getClass());
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
				boolean verticalDir = true;
				boolean autoCalled = false;
				boolean verbose = false;
				boolean test = false;
				double profond = 30.0;
				ResultsTable rt1 = mainUnifor(path, verticalDir, profond,
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

		boolean fast = Prefs.get("prefer.fast", "false").equals("true") ? true
				: false;
		IJ.log("p11rmn_.autoMenu fast= " + fast);

		IJ.log("p11rmn_.autoMenu autoargs= " + autoArgs);
		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p11rmn_");
			selfTestSilent();
			return 0;
		}

		if ((nTokens != MyConst.TOKENS2) && (nTokens != MyConst.TOKENS4)) {
			Msg.msgParamError();
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);

		String[] path = new String[3];
		if (nTokens == MyConst.TOKENS2) {
			path[0] = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path[1] = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			// path3 = lr.getPath(strRiga3, riga2);
		} else {
			path[0] = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path[1] = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
			path[2] = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
		}

		boolean verticalDir = decodeDirez(TableSequence.getDirez(iw2ayvTable,
				vetRiga[0]));
		MyLog.waitHere("verticalDir= " + verticalDir);

		double profond = Double.parseDouble(TableSequence.getProfond(
				iw2ayvTable, vetRiga[0]));

		boolean step = false;
		boolean retry = false;

		if (fast) {
			retry = false;
			boolean autoCalled = true;
			boolean verbose = true;
			boolean test = false;
			ResultsTable rt1 = mainUnifor(path, verticalDir, profond,
					autoCalled, step, verbose, test);
			if (rt1 == null)
				return 0;

			rt1.show("Results");
			UtilAyv.saveResults3(vetRiga, fileDir, iw2ayvTable);

			UtilAyv.afterWork();
		} else
			do {
				// int userSelection1 = UtilAyv.userSelectionAuto(VERSION,
				// TYPE);
				int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]),
						TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

				switch (userSelection1) {
				case ABORT:
					new AboutBox().close();
					return 0;
				case 2:
					new AboutBox().about("Controllo Bobine Superficiali",
							this.getClass());
					retry = true;
					break;
				case 3:
					step = true;
					// retry = false;
					// break;
				case 4:
					// step = false;
					retry = false;
					boolean autoCalled = true;
					boolean verbose = true;
					boolean test = false;
					ResultsTable rt1 = mainUnifor(path, verticalDir, profond,
							autoCalled, step, verbose, test);
					if (rt1 == null)
						return 0;

					rt1.show("Results");
					UtilAyv.saveResults3(vetRiga, fileDir, iw2ayvTable);

					UtilAyv.afterWork();
					break;
				}
			} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	@SuppressWarnings("deprecation")
	public static ResultsTable mainUnifor(String[] path, boolean verticalDir,
			double profond, boolean autoCalled, boolean step, boolean verbose,
			boolean test) {
		boolean accetta = false;
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		do {

			double out2[] = positionSearch(path[0], autoCalled, verticalDir,
					profond, step, verbose, test);

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
			int width = imp1.getWidth();
			int height = imp1.getHeight();

			overlayGrid(imp1, MyConst.P11_GRID_NUMBER, verbose);

			double dimPixel = ReadDicom.readDouble(ReadDicom.readSubstring(
					ReadDicom.readDicomParameter(imp1,
							MyConst.DICOM_PIXEL_SPACING), 2));
			if (verbose)
				imp1.setTitle("DIMENSIONI RETICOLO= "
						+ (dimPixel * (double) height / (double) MyConst.P11_GRID_NUMBER)
						+ " mm");

			int sqNEA = MyConst.P11_NEA_11X11_PIXEL;

			int xMaxima = (int) out2[6];
			int yMaxima = (int) out2[7];
			double xStartRefLine = out2[2];
			double yStartRefLine = out2[3];
			double xEndRefLine = out2[4];
			double yEndRefLine = out2[5];
			int index = 0;
			// MyLog.waitHere("Line1 xStart= " + xStartRefLine + " yStart= "
			// + yStartRefLine + " xEnd= " + xEndRefLine + " yEnd= "
			// + yEndRefLine);

			if (verbose) {

				// =================================================
				imp1.setRoi(new OvalRoi(xMaxima - 4, yMaxima - 4, 8, 8));
				over2.addElement(imp1.getRoi());
				imp1.setOverlay(over2);
				over2.setStrokeColor(Color.red);
				imp1.killRoi();
				imp1.setRoi(new Line(xStartRefLine, yStartRefLine, xEndRefLine,
						yEndRefLine));

				over2.addElement(imp1.getRoi());
				index = over2.size();
				over2.setStrokeColor(Color.red);
				imp1.killRoi();
				imp1.updateAndDraw();
				// =================================================
			}

			ImageProcessor ip1 = imp1.getProcessor();
			//
			// disegno MROI su imp1
			//
			int xCenterRoi = 0;
			int yCenterRoi = 0;
			int xCenterProposed = (int) out2[0];
			int yCenterProposed = (int) out2[1];

			if (!test) {
				// UtilAyv.autoAdjust(imp1, ip1);
				if (imp1.isVisible())
					UtilAyv.backgroundEnhancement(0, 0, 10, imp1);
				int userSelection1 = 0;

				do {
					imp1.setRoi(xCenterProposed - sqNEA / 2, yCenterProposed
							- sqNEA / 2, sqNEA, sqNEA);
					imp1.updateAndDraw();
					userSelection1 = menuPositionMroi();
				} while (userSelection1 == 1);
				//
				// rilettura posizione user-defined
				//
				// new WaitForUserDialog("Do something, then click OK.").show();

				ip1 = imp1.getProcessor();
				Rectangle boundingRectangle = ip1.getRoi();

				if (imp1.isVisible())
					imp1.getWindow().toFront();
				xCenterRoi = boundingRectangle.x;
				yCenterRoi = boundingRectangle.y;

			} else {
				xCenterRoi = xCenterProposed - sqNEA / 2;
				yCenterRoi = yCenterProposed - sqNEA / 2;

				imp1.setRoi(xCenterRoi, yCenterRoi, sqNEA, sqNEA);
				imp1.updateAndDraw();
			}

			// MyLog.waitHere("xCenterRoi= " + xCenterRoi + " yCenterRoi "
			// + yCenterRoi);

			// Rectangle rect1 = imp1.getRoi().getBounds();
			// MyLog.waitHere("Roi center x= " + rect1.getX() + " y= "
			// + rect1.getY());

			if (imp1.isVisible()) {
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.green);
				over2.remove(index - 1);
				imp1.draw();
			}

			double xStartRefLine2 = 0;
			double yStartRefLine2 = 0;
			double xEndRefLine2 = 0;
			double yEndRefLine2 = 0;

			if (verticalDir) {
				MyLog.here();
				xStartRefLine2 = xCenterRoi + sqNEA / 2;
				yStartRefLine2 = 0;
				xEndRefLine2 = xCenterRoi + sqNEA / 2;
				yEndRefLine2 = height;
			} else {
				// MyLog.here();
				xStartRefLine2 = 0;
				yStartRefLine2 = yCenterRoi + sqNEA / 2;
				xEndRefLine2 = width;
				yEndRefLine2 = yCenterRoi + sqNEA / 2;
			}

			// MyLog.waitHere("Line2 xStart= " + xStartRefLine2 + " yStart= "
			// + yStartRefLine2 + " xEnd= " + xEndRefLine2 + " yEnd= "
			// + yEndRefLine2);

			imp1.setRoi(new Line(xStartRefLine2, yStartRefLine2, xEndRefLine2,
					yEndRefLine2));

			// Rectangle rect2 = imp1.getRoi().getBounds();
			// MyLog.waitHere("Line center x= " + rect2.getX() + " y= "
			// + rect2.getY());

			over2.addElement(imp1.getRoi());
			over2.setStrokeColor(Color.green);

			//
			// posiziono la ROI 7x7 all'interno di MROI
			//

			int gap = (sqNEA - MyConst.P11_MROI_7X7_PIXEL) / 2;
			imp1.setRoi(xCenterRoi + gap, yCenterRoi + gap,
					MyConst.P11_MROI_7X7_PIXEL, MyConst.P11_MROI_7X7_PIXEL);
			imp1.updateAndDraw();
			over2.addElement(imp1.getRoi());
			over2.setStrokeColor(Color.green);
			ImageStatistics stat1 = imp1.getStatistics();
			double signal1 = stat1.mean;

			int xFondo = MyConst.P11_X_ROI_BACKGROUND;
			int yFondo = MyConst.P11_Y_ROI_BACKGROUND;
			// if (test)
			// yFondo = yFondo + 40;
			if (step)
				msgMroi();
			//
			// disegno RoiFondo su imp1
			//
			ImageStatistics statFondo = UtilAyv.backCalc(xFondo, yFondo,
					MyConst.P11_DIAM_ROI_BACKGROUND, imp1, step, false, test);

			over2.addElement(imp1.getRoi());
			over2.setStrokeColor(Color.green);

			//
			// disegno MROI su imaDiff
			//
			ImagePlus imaDiff = UtilAyv.genImaDifference(imp1, imp2);
			if (verbose) {
				UtilAyv.showImageMaximized(imaDiff);
				// imp1.getWindow().toFront();
			}
			overlayGrid(imaDiff, MyConst.P11_GRID_NUMBER, verbose);

			imaDiff.resetDisplayRange();
			imaDiff.setOverlay(over3);
			over3.setStrokeColor(Color.green);

			imaDiff.setRoi(xCenterRoi + gap, yCenterRoi + gap,
					MyConst.P11_MROI_7X7_PIXEL, MyConst.P11_MROI_7X7_PIXEL);
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
			imp1.setRoi(xCenterRoi, yCenterRoi, sqNEA, sqNEA);
			imp1.updateAndDraw();

			over2.addElement(imp1.getRoi());
			over2.setStrokeColor(Color.green);

			if (imp1.isVisible())
				imp1.getWindow().toFront();
			//
			// qui, se il numero dei pixel < 121 dovr� incrementare sqR2 e
			// ripetere il loop
			//
			double checkPixels = MyConst.P11_CHECK_PIXEL_MULTIPLICATOR
					* prelimImageNoiseEstimate_MROI;
			int area11x11 = MyConst.P11_NEA_11X11_PIXEL
					* MyConst.P11_NEA_11X11_PIXEL;
			int enlarge = 0;
			int pixx = 0;

			do {
				pixx = countPix(imp1, xCenterRoi - enlarge, yCenterRoi
						- enlarge, sqNEA, checkPixels);

				imp1.setRoi(xCenterRoi - enlarge, yCenterRoi - enlarge, sqNEA,
						sqNEA);
				imp1.updateAndDraw();
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.green);

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

				if ((xCenterRoi + sqNEA - enlarge) >= width
						|| (xCenterRoi - enlarge) <= 0) {

					// if ((xCenterRoi + sqNEA - enlarge) >= width)
					// MyLog.waitHere("prima condizione");
					// if ((xCenterRoi - enlarge) <= 0)
					// MyLog.waitHere("seconda condizione");
					msgNot121();
					// MyLog.waitHere("esco qui");
					return null;
				}
				if ((yCenterRoi + sqNEA - enlarge) >= height
						|| (yCenterRoi - enlarge) <= 0) {
					msgNot121();
					// MyLog.waitHere("esco qui");
					return null;
				}
				if (step && pixx >= area11x11) {
					// MyLog.waitHere();
					msgSqr2OK(pixx);
				}

			} while (pixx < area11x11);

			imp1.setRoi(xCenterRoi - enlarge, yCenterRoi - enlarge, sqNEA,
					sqNEA);
			imp1.updateAndDraw();
			if (imp1.isVisible())
				imp1.getWindow().toFront();
			//
			// calcolo SD su imaDiff quando i corrispondenti pixel
			// di imp1 passano il test
			//
			double[] out1 = devStandardNema(imp1, imaDiff,
					xCenterRoi - enlarge, yCenterRoi - enlarge, sqNEA,
					checkPixels);
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
			int[][] classiSimulata = generaSimulata(xCenterRoi + gap,
					yCenterRoi + gap, MyConst.P11_MROI_7X7_PIXEL, imp1, step,
					verbose, test);

			//
			// calcolo posizione fwhm a met� della MROI
			//
			if (imp1.isVisible())
				imp1.getWindow().toFront();
			int xStartProfile = 0;
			int yStartProfile = 0;
			int xEndProfile = 0;
			int yEndProfile = 0;

			if (test) {
				if (verticalDir) {
					xStartProfile = xCenterRoi + gap
							+ MyConst.P11_MROI_7X7_PIXEL / 2;
					yStartProfile = 1;
					xEndProfile = xStartProfile;
					yEndProfile = height;
				} else {
					xStartProfile = 1;
					yStartProfile = yCenterRoi + gap
							+ MyConst.P11_MROI_7X7_PIXEL / 2;
					xEndProfile = width;
					yEndProfile = yStartProfile;
				}
			} else {
				Line line = selectProfilePosition(xCenterRoi + gap, yCenterRoi
						+ gap, MyConst.P11_MROI_7X7_PIXEL, imp1, verticalDir);

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

			String[][] tabCodici = TableCode.loadTable(MyConst.CODE_FILE);

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
			rt.addValue(3, xCenterRoi);
			rt.addValue(4, yCenterRoi);
			rt.addValue(5, sqNEA);
			rt.addValue(6, sqNEA);

			rt.incrementCounter();
			rt.addLabel(t1, "Rumore_Fondo222");
			rt.addValue(2, statFondo.mean);
			int xRoi = (int) statFondo.roiX;
			int yRoi = (int) statFondo.roiY;
			int widthRoi = (int) statFondo.roiWidth;
			int heightRoi = (int) statFondo.roiHeight;

			rt.addValue(3, xRoi);
			rt.addValue(4, yRoi);
			rt.addValue(5, widthRoi);
			rt.addValue(6, heightRoi);

			rt.incrementCounter();
			rt.addLabel(t1, "SnR");
			rt.addValue(2, snr);
			rt.addValue(3, xCenterRoi);
			rt.addValue(4, yCenterRoi);
			rt.addValue(5, sqNEA);
			rt.addValue(6, sqNEA);

			rt.incrementCounter();
			rt.addLabel(t1, "FWHM");
			rt.addValue(2, outFwhm2[0]);
			rt.addValue(3, xStartProfile);
			rt.addValue(4, yStartProfile);
			rt.addValue(5, xEndProfile);
			rt.addValue(6, yEndProfile);

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

			boolean fast = Prefs.get("prefer.fast", "false").equals("true") ? true
					: false;

			if (fast) {
				accetta = true;
			} else if (autoCalled && !test) {
				accetta = Msg.accettaMenu();
			} else {
				if (!test) {
					accetta = Msg.msgStandalone();
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
				String[] list = { "CTSA2_01testP11", "CTSA2_02testP11",
						"CTSA2_03testP11" };
				String[] path = new InputOutput().findListTestImages2(
						MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;
				double[] vetReference = referenceGe();
				boolean verticalDir = false;
				double profond = 30.0;

				ResultsTable rt1 = mainUnifor(path, verticalDir, profond,
						autoCalled, step, verbose, test);
				if (rt1 == null)
					return;

				double[] vetResults = UtilAyv.vectorizeResults(rt1);
				boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
						MyConst.P11_vetName);
				if (ok)
					Msg.msgTestPassed();
				else
					Msg.msgTestFault();
				UtilAyv.afterWork();
				break;
			}
			case 2:
				// Siemens
				String[] list = { "S12S_01testP11", "S12S_02testP11" };
				String[] path = new InputOutput().findListTestImages2(
						MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;

				double[] vetReference = referenceSiemens();
				boolean verticalDir = true;
				double profond = 30.0;
				ResultsTable rt1 = mainUnifor(path, verticalDir, profond,
						autoCalled, step, verbose, test);
				if (rt1 == null)
					return;
				double[] vetResults = UtilAyv.vectorizeResults(rt1);
				boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
						MyConst.P11_vetName);
				if (ok)
					Msg.msgTestPassed();
				else
					Msg.msgTestFault();
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
		double signal = 838.2244897959183;
		double backNoise = 3.43;
		double snRatio = 342.60901357950996;
		double fwhm = 50.33006324662394;
		double num1 = 1195.0;
		double num2 = 485.0;
		double num3 = 1156.0;
		double num4 = 733.0;
		double num5 = 929.0;
		double num6 = 1137.0;
		double num7 = 1481.0;
		double num8 = 2001.0;
		double num9 = 2791.0;
		double num10 = 3983.0;
		double num11 = 4400.0;
		double num12 = 45245.0;

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
		double signal = 838.2244897959183;
		double backNoise = 3.43;
		double snRatio = 342.60901357950996;
		double fwhm = 50.33006324662394;
		double num1 = 1195.0;
		double num2 = 485.0;
		double num3 = 1156.0;
		double num4 = 733.0;
		double num5 = 929.0;
		double num6 = 1137.0;
		double num7 = 1481.0;
		double num8 = 2001.0;
		double num9 = 2791.0;
		double num10 = 3983.0;
		double num11 = 4400.0;
		double num12 = 45245.0;

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm,
				num1, num2, num3, num4, num5, num6, num7, num8, num9, num10,
				num11, num12 };
		return vetReference;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {

		String[] list = { "S12S_01testP11", "S12S_02testP11" };

		// String[] list = { "S1SA_01testP11", "S1SA_02testP11",
		// "S1SA_03testP11" };

		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		double[] vetReference = referenceSiemens();

		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;
		boolean verticalDir = false;
		double profond = 30.0;

		ResultsTable rt1 = p11rmn_OLD.mainUnifor(path, verticalDir, profond,
				autoCalled, step, verbose, test);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P11_vetName);
		if (ok) {
			IJ.log("Il test di p11rmn_ UNIFORMITA' SUPERFICIALE � stato SUPERATO");
		} else {
			IJ.log("Il test di p11rmn_ UNIFORMITA' SUPERFICIALE evidenzia degli ERRORI");
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
	 * @return [0] sum / pixelcount [1] devStan
	 */

	private static double[] devStandardNema(ImagePlus imp1, ImagePlus imp3,
			int sqX, int sqY, int sqR, double limit) {
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
				}
			}
		}
		results[0] = sumValues / pixelCount;
		double sd1 = calculateStdDev4(pixelCount, sumValues, sumSquare);
		results[1] = sd1;
		return (results);
	}

	/**
	 * Genera l'immagine simulata a 11+1 livelli
	 * 
	 * @param imp1
	 *            immagine da analizzare
	 * @param sqX
	 *            coordinata x della Roi centrale
	 * @param sqY
	 *            coordinata y della Roi centrale
	 * @param sqR
	 *            diametro della Roi centrale
	 * @return immagine simulata a 11+1 livelli
	 */

	private static ImagePlus simulata12Classi(int sqX, int sqY, int sqR,
			ImagePlus imp1) {

		if (imp1 == null) {
			IJ.error("Simula12 ricevuto null");
			return (null);
		}

		int width = imp1.getWidth();
		short[] pixels1 = UtilAyv.truePixels(imp1);
		//
		// disegno MROI per calcoli
		//
		imp1.setRoi(sqX, sqY, sqR, sqR);
		ImageStatistics stat1 = imp1.getStatistics();
		double mean = stat1.mean;
		//
		// limiti classi
		//
		double minus90 = mean * MyConst.MINUS_90_PERC;
		double minus80 = mean * MyConst.MINUS_80_PERC;
		double minus70 = mean * MyConst.MINUS_70_PERC;
		double minus60 = mean * MyConst.MINUS_60_PERC;
		double minus50 = mean * MyConst.MINUS_50_PERC;
		double minus40 = mean * MyConst.MINUS_40_PERC;
		double minus30 = mean * MyConst.MINUS_30_PERC;
		double minus20 = mean * MyConst.MINUS_20_PERC;
		double minus10 = mean * MyConst.MINUS_10_PERC;
		double plus10 = mean * MyConst.PLUS_10_PERC;
		double plus20 = mean * MyConst.PLUS_20_PERC;
		// genero una immagine nera
		ImagePlus impSimulata = NewImage.createShortImage("Simulata", width,
				width, 1, NewImage.FILL_BLACK);
		//
		// nuova immagine simulata vuota
		//
		ShortProcessor processorSimulata = (ShortProcessor) impSimulata
				.getProcessor();
		short[] pixelsSimulata = (short[]) processorSimulata.getPixels();
		//
		// riempimento immagine simulata
		//
		short pixSorgente;
		short pixSimulata;
		int posizioneArrayImmagine = 0;

		for (int y = 0; y < width; y++) {
			for (int x = 0; x < width; x++) {
				posizioneArrayImmagine = y * width + x;
				pixSorgente = pixels1[posizioneArrayImmagine];
				if (pixSorgente > plus20)
					pixSimulata = MyConst.LEVEL_12;
				else if (pixSorgente > plus10)
					pixSimulata = MyConst.LEVEL_11;
				else if (pixSorgente > minus10)
					pixSimulata = MyConst.LEVEL_10;
				else if (pixSorgente > minus20)
					pixSimulata = MyConst.LEVEL_9;
				else if (pixSorgente > minus30)
					pixSimulata = MyConst.LEVEL_8;
				else if (pixSorgente > minus40)
					pixSimulata = MyConst.LEVEL_7;
				else if (pixSorgente > minus50)
					pixSimulata = MyConst.LEVEL_6;
				else if (pixSorgente > minus60)
					pixSimulata = MyConst.LEVEL_5;
				else if (pixSorgente > minus70)
					pixSimulata = MyConst.LEVEL_4;
				else if (pixSorgente > minus80)
					pixSimulata = MyConst.LEVEL_3;
				else if (pixSorgente > minus90)
					pixSimulata = MyConst.LEVEL_2;
				else
					pixSimulata = MyConst.LEVEL_1;

				pixelsSimulata[posizioneArrayImmagine] = pixSimulata;
			}
		}
		processorSimulata.resetMinAndMax();
		return impSimulata;
	} // simula12

	/**
	 * Estrae la numerosit� dell classi dalla simulata
	 * 
	 * @param imp1
	 *            immagine simulata da analizzare
	 * @return numerosit� delle classi in cui per ogni elemento abbiamo
	 *         [valore][numerosit�]
	 */

	public static int[][] numeroPixelsClassi(ImagePlus imp1) {

		if (imp1 == null) {
			IJ.error("numeroPixelClassi ricevuto null");
			return (null);
		}
		int width = imp1.getWidth();

		ImageProcessor ip1 = imp1.getProcessor();
		short[] pixels1 = (short[]) ip1.getPixels();
		int offset = 0;
		int pix1 = 0;

		int[][] vetClassi = { { MyConst.LEVEL_12, 0 }, { MyConst.LEVEL_11, 0 },
				{ MyConst.LEVEL_10, 0 }, { MyConst.LEVEL_9, 0 },
				{ MyConst.LEVEL_8, 0 }, { MyConst.LEVEL_7, 0 },
				{ MyConst.LEVEL_6, 0 }, { MyConst.LEVEL_5, 0 },
				{ MyConst.LEVEL_4, 0 }, { MyConst.LEVEL_3, 0 },
				{ MyConst.LEVEL_2, 0 }, { MyConst.LEVEL_1, 0 } };
		boolean manca = true;

		for (int y1 = 0; y1 < width; y1++) {
			for (int x1 = 0; x1 < (width); x1++) {
				offset = y1 * width + x1;
				pix1 = pixels1[offset];
				manca = true;
				for (int i1 = 0; i1 < vetClassi.length; i1++)
					if (pix1 == vetClassi[i1][0]) {
						vetClassi[i1][1] = vetClassi[i1][1] + 1;
						manca = false;
						break;
					}
				if (manca) {
					ButtonMessages.ModelessMsg("SIMULATA CON VALORE ERRATO="
							+ pix1 + "   <38>", "CONTINUA");
					return (null);
				}
			}
		}
		return (vetClassi);

	} // classi

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
	 *            sopra e sotto la met� altezza
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
		for (int i1 = 0; i1 < profile.length; i1++) {
			if (profile[i1] == min)
				peak = i1;
		}

		double[] out = { fwhm, peak };
		return (out);
	} // calcFwhm

	/**
	 * Mostra a video un profilo con linea a met� picco
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
	 * ricerca dei punti a met� altezza
	 * 
	 * @param profile1
	 *            Vettore con il profilo da analizzare
	 * @return isd[0] punto sotto half a sx, isd[1] punto sopra half a sx,
	 * @return isd[2] punto sotto half a dx, isd[3] punto sopra half a dx
	 */
	private static int[] halfPointSearch(double profile1[]) {
		/*
		 * ATTENZIONE. il nostro profilo standard � il seguente:
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

		// calcolo met� altezza
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
	 * generazione di un immagine simulata, display e salvataggio
	 * 
	 * @param xRoi
	 *            coordinata x roi
	 * @param yRoi
	 *            coordinata y roi
	 * @param diamRoi
	 *            diametro roi
	 * @param imp
	 *            puntatore ImagePlus alla immagine originale
	 * @param step
	 *            funzionamento passo passo
	 * @param verbose
	 * 
	 * @param test
	 *            modo autotest
	 * @return numeriosit� classi simulata
	 */
	private static int[][] generaSimulata(int xRoi, int yRoi, int diamRoi,
			ImagePlus imp, boolean step, boolean verbose, boolean test) {

		int xRoiSimulata = xRoi + (diamRoi - MyConst.P11_NEA_11X11_PIXEL) / 2;
		int yRoiSimulata = yRoi + (diamRoi - MyConst.P11_NEA_11X11_PIXEL) / 2;
		ImagePlus impSimulata = simulata12Classi(xRoiSimulata, yRoiSimulata,
				MyConst.P11_NEA_11X11_PIXEL, imp);
		if (verbose) {
			UtilAyv.showImageMaximized(impSimulata);
			IJ.run("Enhance Contrast", "saturated=0.5");
		}
		// impSimulata.updateAndDraw();
		if (step)
			msgSimulata();
		int[][] classiSimulata = numeroPixelsClassi(impSimulata);
		String patName = ReadDicom.readDicomParameter(imp,
				MyConst.DICOM_PATIENT_NAME);
		String codice = ReadDicom
				.readDicomParameter(imp, MyConst.DICOM_SERIES_DESCRIPTION)
				.substring(0, 4).trim();
		simulataName = fileDir + patName + codice + "sim.zip";

		if (!test)
			new FileSaver(impSimulata).saveAsZip(simulataName);
		return classiSimulata;
	} // generaSimulata

	/**
	 * scelta da parte dell'utente della posizione e direzione del profilo su
	 * cui poi verr� calcolata l'FWHM
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
			ImagePlus imp1, boolean verticalDir) {

		// partiamo da dove � stata posizionata la ROI

		int width = imp1.getWidth();
		int height = imp1.getHeight();
		int xStartProfile = 0;
		int yStartProfile = 0;
		int xEndProfile = 0;
		int yEndProfile = 0;

		if (verticalDir) {
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

		Prefs.set("prefer.p5rmnVert", verticalDir);
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
		ButtonMessages.ModelessMsg("sqR2 OK  poich� pixx=" + pixx, "CONTINUA");
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

		if (in1.length() == 1) {
			if (in1.equals("v") || in1.equals("V")) {
				out = true;
			} else if (in1.equals("h") || in1.equals("H")) {
				out = false;
			} else
				MyLog.here("il codice direzione deve essere v = vertical oppure h = horizontal");
		} else
			MyLog.here("il codice della direzione deve essere di 1 lettera");
		return out;
	}

	/**
	 * 
	 * @param path1
	 * @param autoCalled
	 * @param step
	 * @param verbose
	 * @param test
	 */
	public static double[] positionSearch(String path1, boolean autoCalled,
			boolean verticalDir, double profond, boolean step, boolean verbose,
			boolean test) {
		//
		// ================================================================================
		// Inizio calcoli geometrici
		// ================================================================================
		//
		ImagePlus imp11 = UtilAyv.openImageNoDisplay(path1, true);
		if (imp11 == null)
			new WaitForUserDialog("imp11 = NULL").show();

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(
						ReadDicom.readDicomParameter(imp11,
								MyConst.DICOM_PIXEL_SPACING), 1));

		int width = imp11.getWidth();
		int height = imp11.getHeight();
		//
		// -------------------------------------------------
		// Determinazione del punto di MAXIMA
		// -------------------------------------------------
		//
		ImageProcessor ip11 = imp11.getProcessor();

		double tolerance = 350.0;
		double threshold = 0.0;
		int outputType = MaximumFinder.LIST;
		boolean excludeOnEdges = false;
		boolean isEDM = false;

		MaximumFinder maxFinder = new MaximumFinder();

		double[] rx = null;
		double[] ry = null;
		ResultsTable rt1 = ResultsTable.getResultsTable();
		int nPunti = 0;
		do {
			rt1.reset();
			maxFinder.findMaxima(ip11, tolerance, threshold, outputType,
					excludeOnEdges, isEDM);
			rx = rt1.getColumnAsDoubles(0);
			ry = rt1.getColumnAsDoubles(1);
			tolerance += 10.0;
			nPunti = rx.length;
		} while (nPunti > 1);
		int xMaxima = (int) rx[0];
		int yMaxima = (int) ry[0];

		//
		// -----------------------------------------------------------
		// Calcolo delle effettive coordinate del segmento
		//
		// ----------------------------------------------------------
		//

		double xStartRefLine = 0;
		double yStartRefLine = 0;
		double xEndRefLine = 0;
		double yEndRefLine = 0;

		if (verticalDir) {
			xStartRefLine = rx[0];
			yStartRefLine = 0;
			xEndRefLine = rx[0];
			yEndRefLine = height;
		} else {
			xStartRefLine = 0;
			yStartRefLine = ry[0];
			xEndRefLine = width;
			yEndRefLine = ry[0];
		}

		imp11.setRoi(new Line(xStartRefLine, yStartRefLine, xEndRefLine,
				yEndRefLine));
		//
		// -----------------------------------------------------------
		// Calcolo coordinate centro della MROI
		// ----------------------------------------------------------
		//
		double[] out1 = interpola2(imp11, xStartRefLine, yStartRefLine,
				xEndRefLine, yEndRefLine, xMaxima, yMaxima, profond, dimPixel,
				verticalDir);
		double ax = out1[0];
		double ay = out1[1];
		imp11.setRoi((int) ax - 10, (int) ay - 10, 20, 20);
		Rectangle boundRec3 = imp11.getProcessor().getRoi();
		double xCenterRoi = boundRec3.getCenterX();
		double yCenterRoi = boundRec3.getCenterY();

		if (verticalDir) {
			xStartRefLine = xCenterRoi;
			yStartRefLine = 0;
			xEndRefLine = xCenterRoi;
			yEndRefLine = height;
		} else {
			xStartRefLine = 0;
			yStartRefLine = yCenterRoi;
			xEndRefLine = width;
			yEndRefLine = yCenterRoi;
		}

		imp11.hide();
		double[] out2 = new double[8];
		out2[0] = xCenterRoi;
		out2[1] = yCenterRoi;
		out2[2] = xStartRefLine;
		out2[3] = yStartRefLine;
		out2[4] = xEndRefLine;
		out2[5] = yEndRefLine;
		out2[6] = xMaxima;
		out2[7] = yMaxima;

		return out2;
	}

	public static double angoloRad(double ax, double ay, double bx, double by) {
		//
		// cambio di segno il sin per tenere conto del fatto che l�origine delle
		// coordinate � in alto a sinistra, anzich� in basso
		//
		double dx = ax - bx;
		double dy = by - ay;
		double alf1 = Math.atan2(dy, dx);
		return alf1;

	}

	public static double[] interpola2(ImagePlus imp1, double xStart,
			double yStart, double xEnd, double yEnd, double xMaxima,
			double yMaxima, double prof, double dimPixel, boolean verticalDir) {
		//
		// con ax, ay indico le coordinate del punto sul cerchio con bx, by
		// indico le coordinate del centro
		//
		// IJ.log("-------- interpola --------");
		double tolerance1 = 1e-6;

		if (verticalDir && (Math.abs(xStart - xEnd) > tolerance1)) {
			MyLog.waitHere("Il profilo non � verticale? verticalDir = "
					+ verticalDir + " xStart= " + xStart + " xEnd= " + xEnd);
		} else if ((Math.abs(yStart - yEnd) > tolerance1)) {
			MyLog.waitHere("Il profilo non � orizzontale? verticalDir = "
					+ verticalDir + " yStart= " + yStart + " yEnd= " + yEnd);
		}

		imp1.setRoi(new Line(xStart, yStart, xEnd, yEnd));

		double[] profi1 = ((Line) imp1.getRoi()).getPixels();

		double[] a = Tools.getMinMax(profi1);
		double min = a[0];
		double max = a[1];

		int[] vetHalfPoint = halfPointSearch(profi1);

		// interpolazione lineare sinistra
		double px0 = vetHalfPoint[0];
		double px1 = vetHalfPoint[1];
		double py0 = profi1[vetHalfPoint[0]];
		double py1 = profi1[vetHalfPoint[1]];

		double py2 = (max - min) / 2.0 + min;
		double px2 = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);

		double sx = px2;

		// interpolazione lineare destra
		double px10 = vetHalfPoint[2];
		double px11 = vetHalfPoint[3];
		double py10 = profi1[vetHalfPoint[2]];
		double py11 = profi1[vetHalfPoint[3]];
		double py12 = (max - min) / 2.0 + min;
		double px12 = px10 + (px11 - px10) / (py11 - py10) * (py12 - py10);

		double dx = px12;

		// a questo punto il maxima deve quasi coincidere con uno dei due punti,
		// la direzione sar� dal punto vicino al maxima verso l'altro punto
		// halfHeight e oltre

		double tolerance2 = 4.0;

		if (Math.abs(dx - sx) < tolerance2 * 4) {
			MyLog.waitHere("profondit� utile troppo bassa!");
			return null;
		}
		double start = Double.NaN;
		double point = Double.NaN;
		double end = Double.NaN;

		// double xMaximaPix = xMaxima * dimPixel;
		// MyLog.waitHere("xMaximaPix= " + xMaximaPix + " xMaxima= " + xMaxima
		// + " dx= " + dx + " sx= " + sx);

		if (Math.abs(dx - xMaxima) <= tolerance2) {
			start = dx;
			point = sx;
		} else if (Math.abs(sx - xMaxima) <= tolerance2) {
			start = sx;
			point = dx;
		} else {
			MyLog.waitHere("PROBLEMUN 001");
			return null;
		}

		if (start < point) {
			end = start + prof;
		} else {
			end = start - prof;
		}

		if (end < 0 || end > profi1.length) {
			MyLog.waitHere("PROBLEMUN 002");
			return null;
		}

		// per adesso ho sempre lavorato sul profilo della linea, ora devo
		// riportarmi alle coordinate, a seconda se il mio profilo era verticale
		// oppure orizzontale

		double cx = Double.NaN;
		double cy = Double.NaN;

		if (verticalDir) {
			cx = xStart;
			cy = end;
		} else {
			cx = end;
			cy = yStart;
		}

		double xPoints1[] = new double[10];
		double yPoints1[] = new double[10];
		xPoints1[0] = px0;
		xPoints1[1] = px1;
		xPoints1[2] = px10;
		xPoints1[3] = px11;

		yPoints1[0] = profi1[(int) px0];
		yPoints1[1] = profi1[(int) px1];
		yPoints1[2] = profi1[(int) px10];
		yPoints1[3] = profi1[(int) px11];

		// MyLog.logVector(xPoints1, "xPoints1");
		// MyLog.logVector(yPoints1, "yPoints1");

		double xPoints2[] = new double[10];
		double yPoints2[] = new double[10];
		xPoints2[0] = sx;
		xPoints2[1] = dx;
		xPoints2[2] = xMaxima;
		xPoints2[3] = cx;

		yPoints2[0] = profi1[(int) sx];
		yPoints2[1] = profi1[(int) dx];
		yPoints2[2] = profi1[(int) xMaxima];
		yPoints2[3] = profi1[(int) cx];

		// MyLog.logVector(xPoints2, "xPoints2");
		// MyLog.logVector(yPoints2, "yPoints2");

		// Plot plot2 = MyPlot.basePlot(profi1, "P R O F I L O", Color.GREEN);
		// plot2.draw();
		// plot2.setColor(Color.red);
		// plot2.addPoints(xPoints1, yPoints1, PlotWindow.CIRCLE);
		// plot2.setColor(Color.blue);
		// plot2.addPoints(xPoints2, yPoints2, PlotWindow.CIRCLE);
		// plot2.drawLine(0, max / 2, profi1.length, max / 2);
		// plot2.show();

		// IJ.log("proiezioneX= " + prof * (Math.cos(ang1)));
		// IJ.log("proiezioneY= " + prof * (Math.sin(ang1)));

		// IJ.log("cx= " + IJ.d2s(cx) + " cy= " + IJ.d2s(cy));
		double[] out = new double[2];
		out[0] = cx;
		out[1] = cy;
		// IJ.log("-----------------------------");
		return out;
	}
} // p11rmn_