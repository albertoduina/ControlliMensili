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
import java.util.ArrayList;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.CustomCanvasGeneric;
import utils.ImageUtils;
import utils.InputOutput;
import utils.Msg;
import utils.MyConst;
import utils.MyFileLogger;
import utils.MyLog;
import utils.MyPlot;
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
 * Analizza in maniera automatica o semi-automatica UNIFORMITA', SNR, FWHM per
 * le bobine superficiali
 * 
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */
public class p11rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "p11_rmn_v1.10_13oct11_";

	private String TYPE = " >> CONTROLLO SUPERFICIALI____________";

	// ---------------------------"01234567890123456789012345678901234567890"

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.xls
	 */
	private static String fileDir = "";

	private static String simulataName = "";

	private static boolean previous = false;
	private static boolean init1 = true;
	private static boolean pulse = false; // lasciare, serve anche se segnalato
											// inutilizzato
	private static Color color1 = Color.green;
	private static Color color2 = Color.green;

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

				String path1 = UtilAyv
						.imageSelection("SELEZIONARE PRIMA ACQUISIZIONE PRIMO ECO...");
				if (path1 == null)
					return 0;

				String path2 = UtilAyv
						.imageSelection("SELEZIONARE SECONDA ACQUISIZIONE PRIMO ECO...");
				if (path2 == null)
					return 0;

				String path3 = UtilAyv
						.imageSelection("SELEZIONARE PRIMA  ACQUISIZIONE SECONDO ECO...");
				if (path3 == null)
					return 0;

				int direzione = 1;
				boolean autoCalled = false;
				boolean verbose = false;
				boolean test = false;
				double profond = 30.0;
				boolean fast = false;
				ResultsTable rt1 = mainUnifor(path1, path2, direzione, profond,
						"", autoCalled, step, verbose, test, fast);
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
			Msg.msgParamError();
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);

		String[] path = new String[3];
		String path1 = "";
		String path2 = "";
		String path3 = "";

		if (nTokens == MyConst.TOKENS2) {
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			// path3 = lr.getPath(strRiga3, riga2);
		} else {
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
			path3 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
		}

		int direzione = decodeDirezione(TableSequence.getDirez(iw2ayvTable,
				vetRiga[0]));

		double profond = Double.parseDouble(TableSequence.getProfond(
				iw2ayvTable, vetRiga[0]));

		boolean step = false;
		boolean retry = false;

		if (fast) {
			retry = false;
			boolean autoCalled = true;
			boolean verbose = false;
			boolean test = false;

			String info10 = "code= "
					+ TableSequence.getCode(iw2ayvTable, vetRiga[0])
					+ " coil= "
					+ TableSequence.getCoil(iw2ayvTable, vetRiga[0]) + "  "
					+ (vetRiga[0] + 1) + " / "
					+ TableSequence.getLength(iw2ayvTable);

			ResultsTable rt1 = mainUnifor(path1, path2, direzione, profond,
					info10, autoCalled, step, verbose, test, fast);
			if (rt1 == null)
				return 0;

			// rt1.show("Results");
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
					ResultsTable rt1 = mainUnifor(path1, path2, direzione,
							profond, "", autoCalled, step, verbose, test, fast);
					if (rt1 == null)
						return 0;

					// rt1.show("Results");
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
	public static ResultsTable mainUnifor(String path1, String path2,
			int direzione, double profond, String info10, boolean autoCalled,
			boolean step, boolean verbose, boolean test, boolean fast) {
		boolean accetta = false;
		boolean manualRequired2 = false;
		ResultsTable rt = null;
		boolean fast2 = false;

		// boolean fast = false;
		// if (Prefs.get("prefer.fast", "false").equals("true")) {
		// fast = true;
		// } else {
		// fast = false;
		// }

		UtilAyv.setMeasure(MEAN + STD_DEV);
		do {

			ImagePlus imp11;
			if (fast && !manualRequired2)
				imp11 = UtilAyv.openImageNoDisplay(path1, true);
			// imp11 = UtilAyv.openImageMaximized(path1);
			else
				imp11 = UtilAyv.openImageMaximized(path1);

			if (imp11 == null)
				MyLog.waitHere("Non trovato il file " + path1);

			fast2 = fast && !manualRequired2;

			double out2[] = positionSearch(imp11, autoCalled, direzione,
					profond, info10, step, verbose, test, fast2);

			if (out2 == null) {
				manualRequired2 = true;
			} else {
				manualRequired2 = false;
				ImagePlus imp1 = null;
				ImagePlus imp2 = null;
				Overlay over2 = new Overlay();
				Overlay over3 = new Overlay();

				if (verbose) {
					imp1 = UtilAyv.openImageMaximized(path1);
					imp2 = UtilAyv.openImageNoDisplay(path2, true);
				} else {
					imp1 = UtilAyv.openImageNoDisplay(path1, true);
					imp2 = UtilAyv.openImageNoDisplay(path2, true);
				}
				int width = imp1.getWidth();
				int height = imp1.getHeight();

				double dimPixel = ReadDicom.readDouble(ReadDicom.readSubstring(
						ReadDicom.readDicomParameter(imp1,
								MyConst.DICOM_PIXEL_SPACING), 2));

				int sqNEA = MyConst.P11_NEA_11X11_PIXEL;

				int xMaximum = (int) out2[6];
				int yMaximum = (int) out2[7];
				double xStartRefLine = out2[2];
				double yStartRefLine = out2[3];
				double xEndRefLine = out2[4];
				double yEndRefLine = out2[5];
				int xCenterRoi = (int) out2[0];
				int yCenterRoi = (int) out2[1];

				if (verbose) {

					// =================================================
					imp1.setRoi(new OvalRoi(xMaximum - 4, yMaximum - 4, 8, 8));
					over2.addElement(imp1.getRoi());
					imp1.setOverlay(over2);
					over2.setStrokeColor(color2);
					imp1.killRoi();
					imp1.setRoi(new Line(xStartRefLine, yStartRefLine,
							xEndRefLine, yEndRefLine));
					over2.addElement(imp1.getRoi());
					over2.setStrokeColor(color2);
					imp1.killRoi();
					imp1.setRoi(xCenterRoi - 10, yCenterRoi - 10, 20, 20);
					over2.addElement(imp1.getRoi());
					imp1.killRoi();

					imp1.updateAndDraw();
					if (step)
						MyLog.waitHere();

					// =================================================
				}

				//
				// disegno MROI su imp1
				//

				imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2,
						sqNEA, sqNEA);
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(color2);
				imp1.killRoi();

				imp1.updateAndDraw();
				if (step)
					MyLog.waitHere();

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

				imp1.setRoi(new Line(xStartRefLine2, yStartRefLine2,
						xEndRefLine2, yEndRefLine2));

				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(color2);
				imp1.updateAndDraw();
				if (step)
					MyLog.waitHere();

				//
				// posiziono la ROI 7x7 all'interno di MROI
				//

				int sq7 = MyConst.P11_MROI_7X7_PIXEL;

				imp1.setRoi(xCenterRoi - sq7 / 2, yCenterRoi - sq7 / 2, sq7,
						sq7);
				imp1.updateAndDraw();

				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(color2);
				imp1.updateAndDraw();
				if (step)
					MyLog.waitHere();

				ImageStatistics stat1 = imp1.getStatistics();
				double signal1 = stat1.mean;

				int xFondo = MyConst.P11_X_ROI_BACKGROUND;
				int yFondo = MyConst.P11_Y_ROI_BACKGROUND + 5;
				//
				// disegno RoiFondo su imp1
				//

				ImageStatistics statFondo = UtilAyv.backCalc2(xFondo, yFondo,
						MyConst.P11_DIAM_ROI_BACKGROUND, imp1, step, false,
						test);

				// MyLog.waitHere("Roi Fondo coordinate: x= " + xFondo + " y= "
				// + yFondo + " statFondo.mean= " + statFondo.mean);

				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(color2);

				//
				// disegno MROI su imaDiff
				//
				ImagePlus imaDiff = UtilAyv.genImaDifference(imp1, imp2);
				// ImagePlus imaDiff = UtilAyv.diffIma(imp1, imp2);
				imaDiff.show();

				if (verbose && !fast) {
					UtilAyv.showImageMaximized(imaDiff);
					// imp1.getWindow().toFront();
				}

				imaDiff.resetDisplayRange();
				imaDiff.setOverlay(over3);
				over3.setStrokeColor(color2);

				imaDiff.setRoi(xCenterRoi - sq7 / 2, yCenterRoi - sq7 / 2, sq7,
						sq7);
				over3.addElement(imaDiff.getRoi());
				over3.setStrokeColor(color2);
				imaDiff.updateAndDraw();

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

				if (imp1.isVisible())
					imp1.getWindow().toFront();
				if (imaDiff.isVisible())
					imaDiff.hide();

				imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2,
						sqNEA, sqNEA);
				imp1.updateAndDraw();

				imp1.setOverlay(over2);

				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(color2);
				imp1.updateAndDraw();

				//
				// qui, se il numero dei pixel < 121 dovrò incrementare sqR2 e
				// ripetere il loop
				//
				double checkPixels = MyConst.P11_CHECK_PIXEL_MULTIPLICATOR
						* prelimImageNoiseEstimate_MROI;
				int area11x11 = MyConst.P11_NEA_11X11_PIXEL
						* MyConst.P11_NEA_11X11_PIXEL;
				int enlarge = 0;
				int pixx = 0;
				do {

					boolean paintPixels = false;

					pixx = countPixTest(imp1, xCenterRoi, yCenterRoi, sqNEA,
							checkPixels, paintPixels);

					imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2,
							sqNEA, sqNEA);
					imp1.updateAndDraw();
					over2.addElement(imp1.getRoi());
					over2.setStrokeColor(color2);

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

					// verifico che quando cresce il lato del quadrato non si
					// esca
					// dall'immagine

					if ((xCenterRoi + sqNEA - enlarge) >= width
							|| (xCenterRoi - enlarge) <= 0) {

						msgNot121();
						return null;
					}
					if ((yCenterRoi + sqNEA - enlarge) >= height
							|| (yCenterRoi - enlarge) <= 0) {
						msgNot121();
						return null;
					}
					if (step && pixx >= area11x11) {
						msgSqr2OK(pixx);
					}

				} while (pixx < area11x11);

				imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2,
						sqNEA, sqNEA);
				imp1.updateAndDraw();

				if (imp1.isVisible())
					imp1.getWindow().toFront();
				//
				// calcolo SD su imaDiff quando i corrispondenti pixel
				// di imp1 passano il test
				//
				double[] out1 = devStandardNema(imp1, imaDiff, xCenterRoi
						- enlarge, yCenterRoi - enlarge, sqNEA, checkPixels);
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

				String patName = ReadDicom.readDicomParameter(imp1,
						MyConst.DICOM_PATIENT_NAME);
				String codice = ReadDicom
						.readDicomParameter(imp1,
								MyConst.DICOM_SERIES_DESCRIPTION)
						.substring(0, 4).trim();

				simulataName = fileDir + patName + codice + "sim.zip";

				int[][] classiSimulata = ImageUtils.generaSimulata12classi(
						xCenterRoi, yCenterRoi, sq7, imp1, simulataName, step,
						verbose, test);

				//
				// calcolo posizione fwhm a metà della MROI
				//
				if (imp1.isVisible())
					imp1.getWindow().toFront();
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

				double[] outFwhm2 = analyzeProfile(imp1, xStartProfile,
						yStartProfile, xEndProfile, yEndProfile, dimPixel, step);

				//
				// Salvataggio dei risultati nella ResultsTable

				String[][] tabCodici = TableCode.loadTable(MyConst.CODE_FILE);

				String[] info1 = ReportStandardInfo.getSimpleStandardInfo(
						path1, imp1, tabCodici, VERSION, autoCalled);

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
				rt.addLabel(t1, "Rumore_Fondo");
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

				String[] levelString = { "+20%", "+10%", "-10%", "-10%",
						"-30%", "-40%", "-50%", "-60%", "-70%", "-80%", "-90%",
						"fondo" };

				for (int i1 = 0; i1 < classiSimulata.length; i1++) {
					rt.incrementCounter();
					rt.addLabel(t1, ("Classe" + classiSimulata[i1][0]) + "_"
							+ levelString[i1]);
					rt.addValue(2, classiSimulata[i1][1]);
				}
				if (verbose && !test && !fast) {
					rt.show("Results");
				}

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
			}
		} while (!accetta || manualRequired2);
		return rt;

	}

	/**
	 * Self test execution menu
	 */
	public void selfTestMenu() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			boolean verbose = false;
			boolean ok = false;
			switch (userSelection2) {
			case 1:
				// GE
				verbose = true;
				ok = selfTestGe(verbose);
				if (ok)
					Msg.msgTestPassed();
				else
					Msg.msgTestFault();
				UtilAyv.afterWork();
				break;

			case 2:
				verbose = true;
				ok = selfTestSiemens(verbose);
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
	public static double[] referenceSiemens() {

		double simul = 0.0;
		double signal = 1275.530612244898;
		double backNoise = 7.85;
		double snRatio = 34.43115820571181;
		double fwhm = 38.891139825926565;
		double num1 = 2616.0;
		double num2 = 505.0;
		double num3 = 1163.0;
		double num4 = 661.0;
		double num5 = 764.0;
		double num6 = 910.0;
		double num7 = 1094.0;
		double num8 = 1382.0;
		double num9 = 1799.0;
		double num10 = 2752.0;
		double num11 = 5478.0;
		double num12 = 46412.0;

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
	public static double[] referenceGe() {

		double simul = 0.0;
		double signal = 2278.4897959183672;
		double backNoise = 1.66;
		double snRatio = 94.11744027303934;
		double fwhm = 36.25918394251357;
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

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm,
				num1, num2, num3, num4, num5, num6, num7, num8, num9, num10,
				num11, num12 };
		return vetReference;
	}

	public static boolean selfTestGe(boolean verbose) {
		String[] list = { "CTSA2_01testP11", "CTSA2_03testP11",
				"CTSA2_02testP11" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path[0];
		String path2 = path[2];
		// String path3 = path[1];
		boolean autoCalled = false;
		boolean step = false;
		// boolean verbose = true;
		boolean test = true;
		double[] vetReference = referenceGe();
		int verticalDir = 3;
		double profond = 30.0;
		boolean fast = true;
		ResultsTable rt1 = mainUnifor(path1, path2, verticalDir, profond, "",
				autoCalled, step, verbose, test, fast);
		if (rt1 == null) {
			MyLog.waitHere("rt1==null");
			return false;
		}
		double[] vetResults = UtilAyv.vectorizeResults(rt1);

		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P11_vetName);
		return ok;
	}

	public static boolean selfTestSiemens(boolean verbose) {
		String[] list = { "S1SA_01testP5", "S1SA_02testP5", "S1SA_03testP5" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path[0];
		// String path3 = path[2];
		String path2 = path[1];
		boolean autoCalled = false;
		boolean step = false;
		boolean test = true;
		boolean fast = true;
		// boolean verbose = true;
		double[] vetReference = referenceSiemens();
		int verticalDir = 1;
		double profond = 30.0;
		ResultsTable rt1 = mainUnifor(path1, path2, verticalDir, profond, "",
				autoCalled, step, verbose, test, fast);
		if (rt1 == null)
			return false;
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P11_vetName);
		return ok;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {
		boolean verbose = false;
		boolean ok = selfTestSiemens(verbose);
		if (ok) {
			IJ.log("Il test di p11rmn_ UNIFORMITA' SUPERFICIALE è stato SUPERATO");
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
	 * @param paintPixels
	 *            switch per test
	 * @return pixel che superano la soglia
	 */
	public static int countPixTest(ImagePlus imp1, int sqX, int sqY, int sqR,
			double limit, boolean paintPixels) {
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
			// N.B. la roi qui sotto è volutamente centrata senza togliere
			// lato/2
			imp1.setRoi(sqX, sqY, sqR, sqR);
			imp1.updateAndDraw();
			pixels2 = (short[]) ip1.getPixels();
		}

		for (int y1 = sqY - sqR / 2; y1 <= (sqY + sqR / 2); y1++) {
			offset = y1 * width;
			for (int x1 = sqX - sqR / 2; x1 <= (sqX + sqR / 2); x1++) {
				w = offset + x1;
				if (w < pixels1.length && pixels1[w] > limit) {
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

	public static double[] devStandardNema(ImagePlus imp1, ImagePlus imp3,
			int sqX, int sqY, int sqR, double limit) {
		double[] results = new double[2];
		double value4 = 0.0;
		double sumValues = 0.0;
		double sumSquare = 0.0;

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
	 *            sopra e sotto la metà altezza
	 * @param profile
	 *            Profilo da analizzare
	 * @return out[0]=FWHM, out[1]=peak position
	 */

	private static double[] calcFwhm(int[] vetUpDwPoints, double profile[],
			double dimPixel) {

		// MyLog.logVector(vetUpDwPoints, "vetUpDwPoints");

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
		for (i1 = 0; i1 < len1; i1++) {
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
		for (i1 = downDx; i1 < len1; i1++) {
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

	private static int menuPositionMroi() {
		int userSelection1 = ButtonMessages.ModelessMsg(
				"Posizionare la MROI sull'area della bobina"
						+ "  e premere Accetta", "ACCETTA", "RIDISEGNA");
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

		// TODO
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
			MyLog.waitHere("errore nella direzione in " + MyConst.CODE_FILE
					+ " valore " + in1 + " non previsto");
			out = -1;
		}

		return out;
	}

	/***
	 * 
	 * @param imp11
	 * @param autoCalled
	 * @param direzione
	 * @param profond
	 * @param info10
	 * @param step
	 * @param verbose
	 * @param test
	 * @param fast
	 * @return
	 */
	public static double[] positionSearch(ImagePlus imp11, boolean autoCalled,
			int direzione, double profond, String info10, boolean step,
			boolean verbose, boolean test, boolean fast) {
		//
		// ================================================================================
		// Inizio calcoli geometrici
		// ================================================================================
		//

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(
						ReadDicom.readDicomParameter(imp11,
								MyConst.DICOM_PIXEL_SPACING), 1));

		int width = imp11.getWidth();
		int height = imp11.getHeight();

		overlayGrid(imp11, MyConst.P11_GRID_NUMBER, verbose);
		imp11.updateAndDraw();

		if (verbose)
			imp11.setTitle("DIMENSIONI RETICOLO= "
					+ (dimPixel * (double) height / (double) MyConst.P11_GRID_NUMBER)
					+ " mm");

		Overlay over1 = new Overlay();
		imp11.setOverlay(over1);
		imp11.updateAndDraw();

		double[] out1 = UtilAyv.findMaximumPosition(imp11);
		double xMaximum = out1[0];
		double yMaximum = out1[1];

		// if (fast && (step || test)) {
		imp11.setRoi(new OvalRoi((int) xMaximum - 4, (int) yMaximum - 4, 8, 8));
		over1.addElement(imp11.getRoi());
		over1.setStrokeColor(color1);
		imp11.updateAndDraw();
		if (step)
			MyLog.waitHere("Maximum value= " + out1[2] + " find at x= "
					+ xMaximum + " y= " + yMaximum);
		// }

		// IJ.log("width= " + width + " height= " + height);

		double startX = Double.NaN;
		double startY = Double.NaN;
		double endX = Double.NaN;
		double endY = Double.NaN;
		boolean manualRequired = false;

		// TODO cercare di eliminare il problema delle imnmagini col
		// ribaltamento.
		// ci sono 2 possibilità:
		// 1) sfruttare il fatto che conosciamo la direzione in cui muoverci,
		// per cui possiamo riprendere dall'altro lato dell'immagine
		// 2) richiedere un intervento manuale per il posizionamento (in questo
		// modo l'operatore può addirittura venire invitato a modificare i
		// parametri di acquisizione)
		//

		String strDirez = "";
		// vup = 1 vdw = 2 hsx = 3 hdx = 4
		switch (direzione) {
		case 1:
			strDirez = " verticale a salire";
			startX = 0;
			endX = width;
			startY = out1[1] - profond / dimPixel;
			if (startY < 0) {
				manualRequired = true;
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
				manualRequired = true;
				startY = startY - height;
			}
			endY = startY;
			break;
		case 3:
			strDirez = " orizzontale a sinistra";
			startX = out1[0] - profond / dimPixel;
			if (startX < 0) {
				manualRequired = true;
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
				manualRequired = true;
				startX = startX - width;
			}
			endX = startX;
			startY = 0;
			endY = height;
			break;
		default:
			MyLog.waitHere("direzione non prevista !!!!");
		}

		// MyLog.waitHere("startX= " + startX + " startY= " + startY + " endX= "
		// + endX + " endY= " + endY);

		imp11.setRoi(new Line(startX, startY, endX, endY));
		over1.addElement(imp11.getRoi());
		over1.setStrokeColor(color1);
		imp11.updateAndDraw();
		if (step)
			MyLog.waitHere("selezione automatica direzione = " + strDirez);

		double[] profi1 = ((Line) imp11.getRoi()).getPixels();
		profi1[0] = 0;
		profi1[profi1.length - 1] = 0;

		int[] vetHalfPoint = halfPointSearch(profi1);

		double[] xPoints = new double[vetHalfPoint.length];
		double[] yPoints = new double[vetHalfPoint.length];
		for (int i1 = 0; i1 < vetHalfPoint.length; i1++) {
			xPoints[i1] = (double) vetHalfPoint[i1];
			yPoints[i1] = profi1[vetHalfPoint[i1]];
		}

		// if (step) {
		// Plot plot1 = MyPlot.basePlot(profi1, "PROFILO", Color.blue);
		// plot1.draw();
		// plot1.setColor(Color.red);
		// plot1.addPoints(xPoints, yPoints, PlotWindow.CIRCLE);
		// plot1.show();
		//
		// plot1.show();
		// MyLog.waitHere();
		// }

		double[] fwhm = calcFwhm(vetHalfPoint, profi1, dimPixel);
		double centro = vetHalfPoint[0] + (fwhm[0] / 2) / dimPixel;

		double xCenter[] = { centro };
		double yCenter[] = { profi1[(int) centro] };

		if (step) {
			Plot plot1 = MyPlot.basePlot(profi1, "PROFILO", Color.blue);
			plot1.draw();
			plot1.setColor(Color.red);
			plot1.addPoints(xPoints, yPoints, PlotWindow.CIRCLE);
			plot1.draw();
			plot1.addPoints(xCenter, yCenter, PlotWindow.BOX);
			plot1.show();
		}

		double ax = Double.NaN;
		double ay = Double.NaN;

		if (direzione < 3) {
			ax = centro;
			ay = startY;

		} else {
			ax = startX;
			ay = centro;

		}

		// if (manualRequired) {
		// ax = width / 2;
		// ay = height / 2;
		// }

		if (!fast && (step || test)) {
			imp11.setRoi(new OvalRoi((int) ax - 4, (int) ay - 4, 8, 8));
			over1.addElement(imp11.getRoi());
			over1.setStrokeColor(color1);
			imp11.updateAndDraw();
		}

		imp11.setRoi((int) ax - 10, (int) ay - 10, 20, 20);

		if (!fast) {
			// MyLog.waitMessage(info10
			// + "\n \nVERIFICA E/O MODIFICA MANUALE POSIZIONE ROI");
			MyLog.waitHere(info10
					+ "\n \nVERIFICA E/O MODIFICA MANUALE POSIZIONE ROI");
			manualRequired = false;
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

	/***
	 * Riceve una ImagePlus con impostata una Line, restituisce le coordinate
	 * dei 2 picchi
	 * 
	 * @param imp1
	 * @param dimPixel
	 * @return coordinate picchi
	 */
	public static double[][] profileAnalyzer(ImagePlus imp1, double dimPixel,
			String title, boolean showProfiles) {
		Roi roi11 = imp1.getRoi();
		double[] profi1 = ((Line) roi11).getPixels();

		double[] profi2y = smooth3(profi1, 1);

		double[] profi2x = new double[profi2y.length];
		double xval = 0.;
		for (int i1 = 0; i1 < profi2y.length; i1++) {
			profi2x[i1] = xval;
			xval += dimPixel;
		}

		double[][] profi3 = new double[profi2y.length][2];
		for (int i1 = 0; i1 < profi2y.length; i1++) {
			profi3[i1][0] = profi2x[i1];
			profi3[i1][1] = profi2y[i1];
		}
		ArrayList<ArrayList<Double>> matOut = peakDet(profi3, 100.);
		double[][] peaks1 = new InputOutput()
				.fromArrayListToDoubleTable(matOut);

		double[] xPoints = new double[peaks1[2].length];
		double[] yPoints = new double[peaks1[2].length];
		for (int i1 = 0; i1 < peaks1[2].length; i1++) {
			xPoints[i1] = peaks1[2][i1];
			yPoints[i1] = peaks1[3][i1];

		}

		if (showProfiles) {
			Plot plot2 = MyPlot.basePlot(profi2x, profi2y, title, color2);
			plot2.draw();
			plot2.setColor(color1);
			plot2.addPoints(xPoints, yPoints, PlotWindow.CIRCLE);
			plot2.show();
			new WaitForUserDialog("002 premere  OK").show();
		}

		return peaks1;
	}

	/***
	 * Effettua lo smooth su 3 pixels di un profilo
	 * 
	 * @param profile1
	 *            profilo
	 * @param loops
	 *            numerompassaggi
	 * @return profilo smoothato
	 */
	public static double[] smooth3(double[] profile1, int loops) {

		int len1 = profile1.length;
		double[] profile2 = new double[len1];
		for (int j1 = 1; j1 < len1 - 1; j1++) {
			profile2[j1] = profile1[j1];
		}

		for (int i1 = 0; i1 < loops; i1++) {
			for (int j1 = 1; j1 < len1 - 1; j1++)
				profile2[j1] = (profile2[j1 - 1] + profile2[j1] + profile2[j1 + 1]) / 3;
		}
		return profile2;

	}

	/**
	 * Copied from http://billauer.co.il/peakdet.htm Peak Detection using MATLAB
	 * Author: Eli Billauer
	 * 
	 * @param profile
	 *            profilo da analizzare
	 * @param delta
	 * @return ArrayList con le posizioni del picco minimo e del picco massimo
	 */
	public static ArrayList<ArrayList<Double>> peakDet(double[][] profile,
			double delta) {

		double max = Double.MIN_VALUE;
		double min = Double.MAX_VALUE;
		ArrayList<ArrayList<Double>> matout = new ArrayList<ArrayList<Double>>();

		ArrayList<Double> maxtabx = new ArrayList<Double>();
		ArrayList<Double> maxtaby = new ArrayList<Double>();
		ArrayList<Double> mintabx = new ArrayList<Double>();
		ArrayList<Double> mintaby = new ArrayList<Double>();

		double[] vetx = new double[profile.length];
		double[] vety = new double[profile.length];
		for (int i1 = 0; i1 < profile.length; i1++) {
			vetx[i1] = profile[i1][0];
			vety[i1] = profile[i1][1];
		}
		double maxpos = -1.0;
		double minpos = -1.0;
		boolean lookformax = true;

		for (int i1 = 0; i1 < vety.length; i1++) {
			double valy = vety[i1];
			if (valy > max) {
				max = valy;
				maxpos = vetx[i1];
			}
			if (valy < min) {
				min = valy;
				minpos = vetx[i1];
			}
			stateChange(lookformax);

			if (lookformax) {
				if (valy < max - delta) {
					maxtabx.add((Double) maxpos);
					maxtaby.add((Double) max);
					min = valy;
					minpos = vetx[i1];
					lookformax = false;
				}
			} else {
				if (valy > min + delta) {
					mintabx.add((Double) minpos);
					mintaby.add((Double) min);
					max = valy;
					maxpos = vetx[i1];
					lookformax = true;
				}
			}

		}
		// MyLog.logArrayList(mintabx, "############## mintabx #############");
		// MyLog.logArrayList(mintaby, "############## mintaby #############");
		// MyLog.logArrayList(maxtabx, "############## maxtabx #############");
		// MyLog.logArrayList(maxtaby, "############## maxtaby #############");
		matout.add(mintabx);
		matout.add(mintaby);
		matout.add(maxtabx);
		matout.add(maxtaby);

		return matout;
	}

	public static double[] maxPeakSearch(double[] profile) {

		double[] a = Tools.getMinMax(profile);
		double max = a[1];

		// cerco il picco a partire da 0
		int max00 = -999;
		int max11 = -999;

		for (int i1 = 0; i1 < profile.length; i1++) {
			if (profile[i1] == max) {
				max00 = i1;
			}
		}

		for (int i1 = profile.length - 1; i1 >= 0; i1--) {
			if (profile[i1] == max) {
				max11 = i1;
			}
		}
		if (max00 != max11)
			return null;
		double[] out = new double[2];
		out[0] = (double) max00;
		out[1] = max;
		return out;
	}

	public static void stateChange(boolean input) {
		pulse = false;
		if ((input != previous) && !init1)
			pulse = true;
		init1 = false;
		return;

	}

	public static double angoloRad(double ax, double ay, double bx, double by) {
		//
		// cambio di segno il sin per tenere conto del fatto che lìorigine delle
		// coordinate è in alto a sinistra, anzichè in basso
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
			MyLog.waitHere("Il profilo non è verticale? verticalDir = "
					+ verticalDir + " xStart= " + xStart + " xEnd= " + xEnd);
		} else if ((Math.abs(yStart - yEnd) > tolerance1)) {
			MyLog.waitHere("Il profilo non è orizzontale? verticalDir = "
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
		// la direzione sarà dal punto vicino al maxima verso l'altro punto
		// halfHeight e oltre

		double tolerance2 = 4.0;

		if (Math.abs(dx - sx) < tolerance2 * 4) {
			MyLog.waitHere("profondità utile troppo bassa!");
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

		// Plot plot2 = MyPlot.basePlot(profi1, "P R O F I L O", color2);
		// plot2.draw();
		// plot2.setColor(color1);
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
