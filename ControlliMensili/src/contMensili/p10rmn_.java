package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Line;
import ij.gui.NewImage;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.MaximumFinder;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
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
 * Analizza UNIFORMITA', SNR, FWHM per le bobine superficiali acquisite con save
 * uncombined vale per le immagini circolari
 * 
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */
public class p10rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "p10_rmn_v0.00_09sep11_";

	private String TYPE = " >> CONTROLLO SUPERFICIALI UNCOMBINED_";

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.txt
	 */
	private static String fileDir = "";

	private static String simulataName = "";
	private static boolean previous = false;
	private static boolean init1 = true;
	private static boolean pulse = false; // lasciare, serve

	// private boolean profiVert = false;
	/**
	 * 
	 * @ TODO : il modo di funzionamento che ho pensato � di trovare il punto
	 * sulla circonferenza utilizzando Find Maxima, con una noise tolerance di
	 * almeno 100 si potrebbe fare una routine che parte da un valore basso e lo
	 * modifica fino a che si ottiene un singolo punto. Resta il problema di
	 * definire il centro del fantoccio. L'approccio pi� sempilce potrebbe
	 * essere di fare posizionare una roi circolare grande quanto il fantoccio
	 * (dal FOV, matrice sigla della misura dovrei essere in grado di scegliere
	 * il diametro giusto per il fantoccio. Una volta definito il punto sul
	 * perimetro ed il centro del cerchio, possiamo posizionare in maniera
	 * ripetitiva la nostra ROI? Lo scopriremo solo unendo i punti dall'1 al
	 * 10000000000.
	 * 
	 */

	public void run(String args) {

		fileDir = Prefs.get("prefer.string1", "none");

		if (IJ.versionLessThan("1.43k"))
			return;

		MyFileLogger.logger.info("p10rmn_>>> fileDir = " + fileDir);

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}
		return;

		// String startingDir = "C:/Dati/Test2/";
		// File[] files = new File(startingDir).listFiles();
		// String path1 = null;
		// for (int i1 = 0; i1 < files.length; i1++) {
		// path1 = files[i1].getAbsolutePath();
		// String path2 =
		// "C:/Users/alberto/Repository/git/ControlliMensili/ControlliMensili/test2/HUSA_001testP3";
		// String autoArgs = "0";
		// boolean autoCalled = false;
		// boolean step = false;
		// boolean verbose = true;
		// boolean test = false;
		//
		// mainUnifor(path1, path2, autoArgs, autoCalled, step, verbose, test);
		// }
		//
		// return;
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
				new AboutBox()
						.about("Controllo Uniformit�, con save UNCOMBINED e immagini circolari",
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
						.imageSelection("SELEZIONARE PRIMA IMMAGINE...");
				if (path1 == null)
					return 0;
				String path2 = UtilAyv
						.imageSelection("SELEZIONARE SECONDA IMMAGINE...");
				if (path2 == null)
					return 0;
				boolean autoCalled = false;
				boolean verbose = true;
				boolean test = false;
				mainUnifor(path1, path2, "0", autoCalled, step, verbose, test);

				UtilAyv.afterWork();
				retry = true;
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	public int autoMenu(String autoArgs) {

		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p10rmn_");
			selfTestSilent();
			return 0;
		}

		if ((nTokens != MyConst.TOKENS2) && (nTokens != MyConst.TOKENS4)) {
			Msg.msgParamError();
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);

		String path1 = "";
		String path2 = "";

		if (nTokens == MyConst.TOKENS2) {
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
		} else {
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
		}

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
				new AboutBox()
						.about("Controllo Bobine Array, immagine circolare UNCOMBINED",
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

				mainUnifor(path1, path2, autoArgs, autoCalled, step, verbose,
						test);

				// ResultsTable rt1 = mainUnifor(path, sqX, sqY, autoArgs,
				// verticalProfile, autoCalled, step, verbose, test);
				// if (rt1 == null)
				// return 0;
				//
				// rt1.show("Results");

				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable);

				UtilAyv.afterWork();
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	// public static void prepUnifor(String path1, String path2, String
	// autoArgs,
	// boolean autoCalled, boolean step, boolean verbose, boolean test) {
	//
	//
	// mainUnifor(path1, path2, autoArgs, autoCalled, step, verbose, test);
	//
	// }

	@SuppressWarnings("deprecation")
	public static ResultsTable mainUnifor(String path1, String path2,
			String autoArgs, boolean autoCalled, boolean step, boolean verbose,
			boolean test) {

		boolean accetta = false;
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);

		do {
			//
			// ================================================================================
			// Inizio calcoli geometrici
			// ================================================================================
			//
			ImagePlus imp11 = null;
			if (verbose) {
				imp11 = UtilAyv.openImageMaximized(path1);
			} else {
				imp11 = UtilAyv.openImageNoDisplay(path1, true);
			}

			if (imp11 == null)
				new WaitForUserDialog("imp11 = NULL").show();

			double dimPixel = ReadDicom.readDouble(ReadDicom.readSubstring(
					ReadDicom.readDicomParameter(imp11,
							MyConst.DICOM_PIXEL_SPACING), 1));

			int width = imp11.getWidth();
			int height = imp11.getHeight();
			//
			// -------------------------------------------------
			// Determinazione del cerchio
			// -------------------------------------------------
			//
			IJ.run(imp11, "Smooth", "");
			if (step)
				new WaitForUserDialog("Eseguito SMOOTH").show();
			IJ.run(imp11, "Find Edges", "");
			if (step)
				new WaitForUserDialog("Eseguito FIND EDGES").show();

			Overlay over1 = new Overlay();
			Overlay over2 = new Overlay();
			imp11.setOverlay(over1);

			double[][] peaks1 = new double[4][1];
			double[][] peaks2 = new double[4][1];
			double[][] peaks3 = new double[4][1];
			double[][] peaks4 = new double[4][1];
			boolean showProfiles = step;

			// IJ.log("BISETTRICE ORIZZONTALE");
			imp11.setRoi(new Line(0, height / 2, width, height / 2));
			if (step) {
				imp11.updateAndDraw();
				over1.addElement(imp11.getRoi());
				over1.setStrokeColor(Color.red);
			}

			peaks1 = profileAnalyzer(imp11, dimPixel, "BISETTRICE ORIZZONTALE",
					showProfiles);

			// IJ.log("BISETTRICE VERTICALE");
			imp11.setRoi(new Line(width / 2, 0, width / 2, height));
			if (step) {
				imp11.updateAndDraw();
				over1.addElement(imp11.getRoi());
			}
			peaks2 = profileAnalyzer(imp11, dimPixel, "BISETTRICE VERTICALE",
					showProfiles);

			// IJ.log("DIAGONALE 1");
			imp11.setRoi(new Line(0, 0, width, height));
			if (step) {
				imp11.updateAndDraw();
				over1.addElement(imp11.getRoi());
			}
			peaks3 = profileAnalyzer(imp11, dimPixel,
					"BISETTRICE DIAGONALE SINISTRA", showProfiles);

			// IJ.log("DIAGONALE 2");
			imp11.setRoi(new Line(0, width, height, 0));
			if (step) {
				imp11.updateAndDraw();
				over1.addElement(imp11.getRoi());
			}
			peaks4 = profileAnalyzer(imp11, dimPixel,
					"BISETTRICE DIAGONALE DESTRA", showProfiles);

			int len3 = peaks1[2].length + peaks2[2].length + peaks3[2].length
					+ peaks4[2].length;

			int[] xPoints3 = new int[len3];
			int[] yPoints3 = new int[len3];
			int j1 = -1;
			for (int i1 = 0; i1 < peaks1[2].length; i1++) {
				j1++;
				xPoints3[j1] = (int) (peaks1[2][i1] / dimPixel);
				yPoints3[j1] = (int) ((double) (height / 2));
			}
			for (int i1 = 0; i1 < peaks2[2].length; i1++) {
				j1++;
				xPoints3[j1] = (int) ((double) (width / 2));
				yPoints3[j1] = (int) (peaks2[2][i1] / dimPixel);
			}
			for (int i1 = 0; i1 < peaks3[2].length; i1++) {
				j1++;
				xPoints3[j1] = (int) (peaks3[2][i1] / dimPixel * Math.sin(Math
						.toRadians(45 + 90)));
				yPoints3[j1] = (int) (peaks3[2][i1] / dimPixel * Math.sin(Math
						.toRadians(45 + 90)));
			}
			for (int i1 = 0; i1 < peaks4[2].length; i1++) {
				j1++;
				xPoints3[j1] = (int) ((peaks4[2][i1] / (dimPixel * Math.sqrt(2))));
				yPoints3[j1] = (int) ((double) height - peaks4[2][i1]
						/ (dimPixel * Math.sqrt(2)));
			}

			// ----------------------------------------------------------------------
			// Verifica di avere trovato almeno 3 punti, altrimenti chiede la
			// selezione manuale del cerchio
			// -------------------------------------------------------------------
			if (xPoints3.length >= 3) {
				imp11.setRoi(new PointRoi(xPoints3, yPoints3, xPoints3.length));
				if (step) {
					imp11.updateAndDraw();
					over1.addElement(imp11.getRoi());
					over1.setStrokeColor(Color.red);
					imp11.setOverlay(over1);
					imp11.updateAndDraw();
					new WaitForUserDialog("Premere OK").show();
				}

				fitCircle(imp11);

				if (step) {
					over1.addElement(imp11.getRoi());
					over1.setStrokeColor(Color.red);
				}

			} else {
				imp11.setRoi(new OvalRoi((width / 2) - 100, (height / 2) - 100,
						200, 200));
				new WaitForUserDialog(
						"Non si riescono a determinare le coordinate di almeno 3 punti del cerchio,\nposizionare a mano una ROI circolare di diametro uguale al fantoccio e\npremere  OK")
						.show();
			}

			Rectangle boundRec = imp11.getProcessor().getRoi();

			// x1 ed y1 sono le due coordinate del centro

			int x1 = boundRec.x + boundRec.width / 2;
			int y1 = boundRec.y + boundRec.height / 2;
			// int diamRoi1 = boundRec.width;
			// IJ.log("Il centro cerchio � x=" + x1 + " y=" + y1);
			//
			// ----------------------------------------------------------
			// disegno la ROI del centro, a solo scopo dimostrativo !
			// ----------------------------------------------------------
			//
			over1.setStrokeColor(Color.red);
			imp11.setOverlay(over1);

			if (verbose) {
				imp11.setRoi(new OvalRoi(x1 - 4, y1 - 4, 8, 8));
				Roi roi1 = imp11.getRoi();
				if (roi1 == null)
					IJ.log("roi1==null");
				over1.addElement(imp11.getRoi());
				over1.setStrokeColor(Color.red);

				imp11.killRoi();
			}
			ImageProcessor ip11 = imp11.getProcessor();

			//
			// -------------------------------------------------
			// Determinazione del punto di MAXIMA
			// -------------------------------------------------
			//
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

			// IJ.log("tolerance=" + tolerance);
			//
			// ----------------------------------------------------------
			// disegno la ROI del maxima, a solo scopo dimostrativo !
			// ----------------------------------------------------------
			//

			// x1 ed y1 sono le due coordinate del punto di maxima

			int x11 = (int) rx[0];
			int y11 = (int) ry[0];

			if (verbose) {
				imp11.setRoi(new OvalRoi(x11 - 4, y11 - 4, 8, 8));
				over1.addElement(imp11.getRoi());
				over1.setStrokeColor(Color.red);
			}
			imp11.killRoi();

			//
			// -----------------------------------------------------------
			// Calcolo delle effettive coordinate del segmento
			// centro-circonferenza
			// ----------------------------------------------------------
			//
			double xStartRefLine = (double) x1;
			double yStartRefLine = (double) y1;
			double xEndRefLine = rx[0];
			double yEndRefLine = ry[0];

			imp11.setRoi(new Line(x1, y1, (int) rx[0], (int) ry[0]));
			over1.addElement(imp11.getRoi());
			over1.setStrokeColor(Color.red);

			double prof = 20;
			//
			// -----------------------------------------------------------
			// Calcolo coordinate centro della MROI
			// ----------------------------------------------------------
			//
			double[] out1 = interpola(xEndRefLine, yEndRefLine, xStartRefLine,
					yStartRefLine, prof / dimPixel);
			double ax = out1[0];
			double ay = out1[1];
			imp11.setRoi((int) ax - 10, (int) ay - 10, 20, 20);

			if (!test)
				MyLog.waitHere("MODIFICA MANUALE POSIZIONE ROI");

			Rectangle boundRec3 = imp11.getProcessor().getRoi();
			int xCenterRoi = boundRec3.x + boundRec3.width / 2;
			int yCenterRoi = boundRec3.y + boundRec3.height / 2;

			if (step)
				new WaitForUserDialog(
						"FINE CALCOLI GEOMETRICI SU IMMAGINE ELABORATA").show();

			// ============================================================================
			// Fine calcoli geometrici
			// Inizio calcoli Uniformit�
			// ============================================================================

			ImagePlus imp1 = null;
			ImagePlus imp2 = null;
			if (verbose) {
				imp1 = UtilAyv.openImageMaximized(path1);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			} else {
				imp1 = UtilAyv.openImageNoDisplay(path1, true);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			}
			ImagePlus imaDiff = UtilAyv.genImaDifference(imp1, imp2);

			int sqNEA = MyConst.P10_NEA_11X11_PIXEL;
			// disegno MROI gi� predeterminata
			int sqX = xCenterRoi;
			int sqY = yCenterRoi;
			imp1.setOverlay(over2);
			over2.setStrokeColor(Color.red);

			if (verbose) {
				// =================================================
				imp1.setRoi(xCenterRoi - 10, yCenterRoi - 10, 20, 20);
				over2.addElement(imp1.getRoi());

				imp1.killRoi();
				imp1.setRoi(new OvalRoi(x1 - 4, y1 - 4, 8, 8));
				over2.addElement(imp1.getRoi());
				imp1.killRoi();
				imp1.setRoi(new OvalRoi(x11 - 4, y11 - 4, 8, 8));
				over2.addElement(imp1.getRoi());
				imp1.setRoi(new Line(x1, y1, (int) rx[0], (int) ry[0]));
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.red);
				imp1.updateAndDraw();
				// =================================================
			}

			imp1.setRoi(sqX - sqNEA / 2, sqY - sqNEA / 2, sqNEA, sqNEA);
			if (verbose) {
				over2.addElement(imp1.getRoi());
			}
			imp1.updateAndDraw();

			// MyLog.waitHere("POSIZIONE MROI");
			if (step)
				new WaitForUserDialog("MROI 11 x 11 Premere  OK").show();

			//
			// posiziono la ROI 7x7 all'interno di MROI
			//

			int sq7 = MyConst.P10_MROI_7X7_PIXEL;
			int gap = (sqNEA - MyConst.P10_MROI_7X7_PIXEL) / 2;
			imp1.setRoi(sqX - sq7 / 2, sqY - sq7 / 2, sq7, sq7);
			if (verbose) {
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.red);
			}
			imp1.updateAndDraw();
			if (step)
				new WaitForUserDialog("MROI 7 x 7 Premere  OK").show();

			ImageStatistics stat1 = imp1.getStatistics();
			double signal1 = stat1.mean;

			int xFondo = MyConst.P10_X_ROI_BACKGROUND;
			int yFondo = MyConst.P10_Y_ROI_BACKGROUND;
			int dFondo = MyConst.P10_DIAM_ROI_BACKGROUND;
			if (step)
				msgMroi();
			//
			// disegno RoiFondo su imp1
			//
			Boolean circular = true;
			ImageStatistics statFondo = backCalcP10(xFondo, yFondo, dFondo,
					imp1, step, circular, test);

			//
			// disegno MROI su imaDiff
			//

			imaDiff.resetDisplayRange();
			imaDiff.setRoi(sqX + gap, sqY + gap, MyConst.P10_MROI_7X7_PIXEL,
					MyConst.P10_MROI_7X7_PIXEL);
			imaDiff.updateAndDraw();
			ImageStatistics statImaDiff = imaDiff.getStatistics();
			imaDiff.updateAndDraw();
			if (imaDiff.isVisible())
				imaDiff.getWindow().toFront();

			if (step)
				msgMroi();

			if (imaDiff.isVisible())
				imaDiff.getWindow().toBack();

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
			imp1.setRoi(sqX - sqNEA / 2, sqY - sqNEA / 2, sqNEA, sqNEA);
			imp1.updateAndDraw();
			if (imp1.isVisible())
				imp1.getWindow().toFront();
			//
			// qui, se il numero dei pixel < 121 dovr� incrementare sqR2 e
			// ripetere il loop
			//

			double checkPixels = MyConst.P10_CHECK_PIXEL_MULTIPLICATOR
					* prelimImageNoiseEstimate_MROI;
			int area11x11 = MyConst.P10_NEA_11X11_PIXEL
					* MyConst.P10_NEA_11X11_PIXEL;
			int enlarge = 0;
			int pixx = 0;
			int xPos = sqX - sqNEA / 2 - enlarge;
			int yPos = sqY - sqNEA / 2 - enlarge;

			do {

				pixx = countPixTest(imp1, xPos, yPos, sqNEA, checkPixels, false);

				imp1.setRoi(xPos, yPos, sqNEA, sqNEA);
				imp1.updateAndDraw();

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

			if (imp1.isVisible())
				imp1.getWindow().toFront();

			imp1.setRoi(xPos, yPos, sqNEA, sqNEA);
			imp1.updateAndDraw();
			//
			// calcolo SD su imaDiff quando i corrispondenti pixel
			// di imp1 passano il test
			//
			double[] out11 = devStandardNema(imp1, imaDiff, xPos, yPos, sqNEA,
					checkPixels);
			if (step)
				msgDisplayMean4(out11[0], out11[1]);
			//
			// calcolo SNR finale
			//
			double snr = signal1 / (out11[1] / Math.sqrt(2));
			if (step)
				msgSnr(snr);

			//
			// calcolo simulata
			//
			int[][] classiSimulata = generaSimulata(sqX + gap, sqY + gap,
					MyConst.P10_MROI_7X7_PIXEL, imp1, step, false, test);
			//
			// calcolo posizione fwhm a met� della MROI
			//
			if (imp1.isVisible())
				imp1.getWindow().toFront();
			//
			// ----------------------------------------------------------
			// Calcolo FWHM
			// la direzione su cui verr� preso il profilo � quella centro
			// cerchio - centro roi, il segmento raggiunge i bordi
			// -----------------------------------------------------------
			//

			double[] out2 = crossing(xStartRefLine, yStartRefLine, xCenterRoi,
					yCenterRoi, width, height);

			int xStartProfile = (int) Math.round(out2[0]);
			int yStartProfile = (int) Math.round(out2[1]);
			int xEndProfile = (int) Math.round(out2[2]);
			int yEndProfile = (int) Math.round(out2[3]);

			imp1.setRoi(new Line(xStartProfile, yStartProfile, xEndProfile,
					yEndProfile));
			imp1.updateAndDraw();

			if (imp1.isVisible())
				imp1.getWindow().toFront();

			step = false;
			double[] outFwhm2 = analyzeProfile(imp1, xStartProfile,
					yStartProfile, xEndProfile, yEndProfile, dimPixel, step);

			//
			// Salvataggio dei risultati nella ResultsTable
			//
			String[][] tabCodici = TableCode.loadTable(MyConst.CODE_FILE);

			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1,
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
			rt.addLabel(t1, "Rumore_Fondo");
			rt.addValue(2, statFondo.mean);
			rt.addValue(3, statFondo.roiX);
			rt.addValue(4, statFondo.roiY);
			rt.addValue(5, statFondo.roiWidth);
			rt.addValue(6, statFondo.roiHeight);

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

				String home1 = findTestImages();
				String path1 = home1 + "A001_testP10";
				String path2 = home1 + "A002_testP10";

				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;

				mainUnifor(path1, path2, autoArgs, autoCalled, step, verbose,
						test);

			}
			case 2:
				// Siemens
				String home1 = findTestImages();
				String path1 = home1 + "A001_testP10";
				String path2 = home1 + "A002_testP10";

				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;

				mainUnifor(path1, path2, autoArgs, autoCalled, step, verbose,
						test);
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
		double signal = 427.57142857142856;
		double backNoise = 11.1125;
		double snRatio = 41.90107231706538;
		double fwhm = 14.451890461530075;
		double num1 = 1012.0;
		double num2 = 595.0;
		double num3 = 2924.0;
		double num4 = 4410.0;
		double num5 = 4639.0;
		double num6 = 3046.0;
		double num7 = 3235.0;
		double num8 = 3688.0;
		double num9 = 4040.0;
		double num10 = 3386.0;
		double num11 = 620.0;
		double num12 = 33941.0;

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
		double signal = 427.57142857142856;
		double backNoise = 11.1125;
		double snRatio = 41.90107231706538;
		double fwhm = 14.451890461530075;
		double num1 = 1012.0;
		double num2 = 595.0;
		double num3 = 2924.0;
		double num4 = 4410.0;
		double num5 = 4639.0;
		double num6 = 3046.0;
		double num7 = 3235.0;
		double num8 = 3688.0;
		double num9 = 4040.0;
		double num10 = 3386.0;
		double num11 = 620.0;
		double num12 = 33941.0;

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm,
				num1, num2, num3, num4, num5, num6, num7, num8, num9, num10,
				num11, num12 };
		return vetReference;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {
		String home1 = findTestImages();
		String path1 = home1 + "A001_testP10";
		String path2 = home1 + "A002_testP10";

		double[] vetReference = referenceSiemens();

		String autoArgs = "-1";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;

		ResultsTable rt1 = mainUnifor(path1, path2, autoArgs, autoCalled, step,
				verbose, test);

		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P10_vetName);
		if (ok) {
			IJ.log("Il test di p10rmn_ UNIFORMITA' SUPERFICIALE � stato SUPERATO");
		} else {
			IJ.log("Il test di p10rmn_ UNIFORMITA' SUPERFICIALE evidenzia degli ERRORI");
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
	 * @param test
	 *            switch per test
	 * @return pixel che superano la soglia
	 */
	public static int countPixTest(ImagePlus imp1, int sqX, int sqY, int sqR,
			double limit, boolean test) {
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
		if (test) {
			// N.B. la roi qui sotto � volutamente centrata senza togliere
			// lato/2
			imp1.setRoi(sqX, sqY, sqR, sqR);
			imp1.updateAndDraw();
			pixels2 = (short[]) ip1.getPixels();
		}

		for (int y1 = sqY; y1 < (sqY + sqR); y1++) {
			offset = y1 * width;
			for (int x1 = sqX; x1 < (sqX + sqR); x1++) {
				w = offset + x1;
				if (w < pixels1.length && pixels1[w] > limit) {
					if (test)
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
		imp1.setRoi(sqX - sqR / 2, sqY - sqR / 2, sqR, sqR);
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

		/*
		 * le seguenti istruzioni sono state superate dalla release 1.40a di
		 * ImageJ. Tale cambiamento � dovuto alle modifiche apportate a
		 * ij\ImagePlus.java, in pratica se l'immagine � calibrata la
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
		// double fwhm = (dx - sx);

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

		int xRoiSimulata = xRoi + (diamRoi - MyConst.P10_NEA_11X11_PIXEL) / 2;
		int yRoiSimulata = yRoi + (diamRoi - MyConst.P10_NEA_11X11_PIXEL) / 2;

		ImagePlus impSimulata = simulata12Classi(xRoiSimulata, yRoiSimulata,
				MyConst.P10_NEA_11X11_PIXEL, imp);
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
	}

	/**
	 * Read preferences from IJ_Prefs.txt
	 * 
	 * @param width
	 *            image width
	 * @param height
	 *            image height
	 * @param limit
	 *            border limit for object placement on image
	 * @return
	 */
	public static int[] readPreferences(int width, int height, int limit) {

		int diam = ReadDicom.readInt(Prefs.get("prefer.p10rmnDiamFantoc",
				Integer.toString(width * 2 / 3)));
		int xRoi1 = ReadDicom.readInt(Prefs.get("prefer.p10rmnXRoi1",
				Integer.toString(height / 2 - diam / 2)));
		int yRoi1 = ReadDicom.readInt(Prefs.get("prefer.p10rmnYRoi1",
				Integer.toString(width / 2 - diam / 2)));
		if (diam < limit)
			diam = height * 2 / 3;
		if (xRoi1 < limit)
			xRoi1 = height / 2 - diam / 2;
		if (yRoi1 < limit)
			yRoi1 = width / 2 - diam / 2;
		int[] defaults = { xRoi1, yRoi1, diam };
		return defaults;
	}

	/***
	 * find255 serve ad analizzare i risultati di findMaxima che mette a 255 i
	 * pixel di MAXIMA
	 * 
	 * @param bp1
	 * @return
	 */
	public static int[][] find255(ByteProcessor bp1) {
		int width = bp1.getWidth();
		byte[] pixels = (byte[]) bp1.getPixels();
		int count = 0;
		for (int i1 = 0; i1 > pixels.length; i1++) {
			if (pixels[i1] == 255) {
				count++;
			}
		}
		int[][] result = new int[2][count];
		count = 0;
		for (int i1 = 0; i1 > pixels.length; i1++) {
			if (pixels[i1] == 255) {
				int y = i1 / width;
				int x = i1 - y;
				result[0][count] = x;
				result[1][count] = y;
				count++;
			}
		}
		return result;
	}

	private static void msgMainRoiPositioning() {
		ButtonMessages.ModelessMsg(
				"Posizionare ROI diamFantoccio e premere CONTINUA", "CONTINUA");
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

	public static double[] interpola(double ax, double ay, double bx,
			double by, double prof) {
		//
		// con ax, ay indico le coordinate del punto sul cerchio con bx, by
		// indico le coordinate del centro
		//
		// IJ.log("-------- interpola --------");

		double ang1 = angoloRad(ax, ay, bx, by);

		// IJ.log("Math.sin(ang1)= " + Math.sin(ang1) + " Math.cos(ang1)= "
		// + Math.cos(ang1));

		double prof2 = prof * (Math.cos(ang1));

		// IJ.log("prof= " + IJ.d2s(prof) + " prof2= " + IJ.d2s(prof2));

		double cx = 0;
		double cy = 0;
		// IJ.log("proiezioneX= " + prof * (Math.cos(ang1)));
		// IJ.log("proiezioneY= " + prof * (Math.sin(ang1)));

		cx = ax - prof * (Math.cos(ang1));
		cy = ay + prof * (Math.sin(ang1));

		// IJ.log("cx= " + IJ.d2s(cx) + " cy= " + IJ.d2s(cy));
		double[] out = new double[2];
		out[0] = cx;
		out[1] = cy;
		// IJ.log("-----------------------------");
		return out;
	}

	/***
	 * Effettua lo smooth su 3 pixels di un profilo
	 * 
	 * @param profile1
	 *            profilo
	 * @param loops
	 *            numerompassaggi
	 * @return
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
	 * calcolo ERF
	 * 
	 * @param profile1
	 *            profilo da elaborare
	 * @param invert
	 *            true se da invertire
	 * @return profilo con ERF
	 */
	public static double[] createErf(double[] profile1, boolean invert) {

		int len1 = profile1.length;

		double[] erf = new double[len1];
		if (invert) {
			for (int j1 = 0; j1 < len1 - 1; j1++)
				erf[j1] = (profile1[j1] - profile1[j1 + 1]) * (-1);

		} else {
			for (int j1 = 0; j1 < len1 - 1; j1++)
				erf[j1] = (profile1[j1] - profile1[j1 + 1]);
		}
		erf[len1 - 1] = erf[len1 - 2];
		return (erf);
	} // createErf

	// public Plot basePlot(double[] profile, String title) {
	// int len1 = profile.length;
	// double[] xcoord1 = new double[len1];
	// for (int j = 0; j < len1; j++)
	// xcoord1[j] = j;
	// Plot plot = new Plot(title, "pixel", "valore", xcoord1, profile);
	// plot.setColor(Color.red);
	// return plot;
	// }

	public static double linearInterpolation(double x0, double y0, double x1,
			double y1, double x2) {

		double y2 = y0 + ((x2 - x0) * y1 - (x2 - x0) * y0) / (x1 - x0);

		return y2;
	}

	/***
	 * Copied from http://billauer.co.il/peakdet.htm Peak Detection using MATLAB
	 * Author: Eli Billauer
	 * 
	 * @param vety
	 * @param delta
	 * @return
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

	public static void stateChange(boolean input) {
		pulse = false;
		if ((input != previous) && !init1)
			pulse = true;
		init1 = false;
		return;

	}

	public static Double toDouble(double in) {
		Double out = new Double(in);
		return out;
	}

	/***
	 * Riceve una ImagePlus con impostata una Line, restituisce le coordinate
	 * dei 2 picchi
	 * 
	 * @param imp1
	 * @param dimPixel
	 * @return
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
			Plot plot2 = MyPlot.basePlot(profi2x, profi2y, title, Color.GREEN);
			plot2.draw();
			plot2.setColor(Color.red);
			plot2.addPoints(xPoints, yPoints, PlotWindow.CIRCLE);
			plot2.show();
			new WaitForUserDialog("002 premere  OK").show();
		}

		return peaks1;
	}

	/***
	 * Questo � il fitCircle preso da ImageJ (ij.plugins.Selection.java, con
	 * sostituito imp.setRoi a IJ.makeOval
	 * 
	 * if selection is closed shape, create a circle with the same area and
	 * centroid, otherwise use<br>
	 * the Pratt method to fit a circle to the points that define the line or
	 * multi-point selection.<br>
	 * Reference: Pratt V., Direct least-squares fitting of algebraic surfaces",
	 * Computer Graphics, Vol. 21, pages 145-152 (1987).<br>
	 * Original code: Nikolai Chernov's MATLAB script for Newton-based Pratt
	 * fit.<br>
	 * (http://www.math.uab.edu/~chernov/cl/MATLABcircle.html)<br>
	 * Java version:
	 * https://github.com/mdoube/BoneJ/blob/master/src/org/doube/geometry
	 * /FitCircle.java<br>
	 * 
	 * @authors Nikolai Chernov, Michael Doube, Ved Sharma
	 */
	public static void fitCircle(ImagePlus imp) {
		Roi roi = imp.getRoi();

		if (roi == null) {
			IJ.error("Fit Circle", "Selection required");
			return;
		}

		if (roi.isArea()) { // create circle with the same area and centroid
			ImageProcessor ip = imp.getProcessor();
			ip.setRoi(roi);
			ImageStatistics stats = ImageStatistics.getStatistics(ip,
					Measurements.AREA + Measurements.CENTROID, null);
			double r = Math.sqrt(stats.pixelCount / Math.PI);
			imp.killRoi();
			int d = (int) Math.round(2.0 * r);
			imp.setRoi(new OvalRoi((int) Math.round(stats.xCentroid - r),
					(int) Math.round(stats.yCentroid - r), d, d));

			// IJ.makeOval((int) Math.round(stats.xCentroid - r),
			// (int) Math.round(stats.yCentroid - r), d, d);
			return;
		}

		Polygon poly = roi.getPolygon();
		int n = poly.npoints;
		int[] x = poly.xpoints;
		int[] y = poly.ypoints;
		if (n < 3) {
			IJ.error("Fit Circle",
					"At least 3 points are required to fit a circle.");
			return;
		}

		// calculate point centroid
		double sumx = 0, sumy = 0;
		for (int i = 0; i < n; i++) {
			sumx = sumx + poly.xpoints[i];
			sumy = sumy + poly.ypoints[i];
		}
		double meanx = sumx / n;
		double meany = sumy / n;

		// calculate moments
		double[] X = new double[n], Y = new double[n];
		double Mxx = 0, Myy = 0, Mxy = 0, Mxz = 0, Myz = 0, Mzz = 0;
		for (int i = 0; i < n; i++) {
			X[i] = x[i] - meanx;
			Y[i] = y[i] - meany;
			double Zi = X[i] * X[i] + Y[i] * Y[i];
			Mxy = Mxy + X[i] * Y[i];
			Mxx = Mxx + X[i] * X[i];
			Myy = Myy + Y[i] * Y[i];
			Mxz = Mxz + X[i] * Zi;
			Myz = Myz + Y[i] * Zi;
			Mzz = Mzz + Zi * Zi;
		}
		Mxx = Mxx / n;
		Myy = Myy / n;
		Mxy = Mxy / n;
		Mxz = Mxz / n;
		Myz = Myz / n;
		Mzz = Mzz / n;

		// calculate the coefficients of the characteristic polynomial
		double Mz = Mxx + Myy;
		double Cov_xy = Mxx * Myy - Mxy * Mxy;
		double Mxz2 = Mxz * Mxz;
		double Myz2 = Myz * Myz;
		double A2 = 4 * Cov_xy - 3 * Mz * Mz - Mzz;
		double A1 = Mzz * Mz + 4 * Cov_xy * Mz - Mxz2 - Myz2 - Mz * Mz * Mz;
		double A0 = Mxz2 * Myy + Myz2 * Mxx - Mzz * Cov_xy - 2 * Mxz * Myz
				* Mxy + Mz * Mz * Cov_xy;
		double A22 = A2 + A2;
		double epsilon = 1e-12;
		double ynew = 1e+20;
		int IterMax = 20;
		double xnew = 0;
		int iterations = 0;

		// Newton's method starting at x=0
		for (int iter = 1; iter <= IterMax; iter++) {
			iterations = iter;
			double yold = ynew;
			ynew = A0 + xnew * (A1 + xnew * (A2 + 4. * xnew * xnew));
			if (Math.abs(ynew) > Math.abs(yold)) {
				if (IJ.debugMode)
					IJ.log("Fit Circle: wrong direction: |ynew| > |yold|");
				xnew = 0;
				break;
			}
			double Dy = A1 + xnew * (A22 + 16 * xnew * xnew);
			double xold = xnew;
			xnew = xold - ynew / Dy;
			if (Math.abs((xnew - xold) / xnew) < epsilon)
				break;
			if (iter >= IterMax) {
				if (IJ.debugMode)
					IJ.log("Fit Circle: will not converge");
				xnew = 0;
			}
			if (xnew < 0) {
				if (IJ.debugMode)
					IJ.log("Fit Circle: negative root:  x = " + xnew);
				xnew = 0;
			}
		}
		if (IJ.debugMode)
			IJ.log("Fit Circle: n=" + n + ", xnew=" + IJ.d2s(xnew, 2)
					+ ", iterations=" + iterations);

		// calculate the circle parameters
		double DET = xnew * xnew - xnew * Mz + Cov_xy;
		double CenterX = (Mxz * (Myy - xnew) - Myz * Mxy) / (2 * DET);
		double CenterY = (Myz * (Mxx - xnew) - Mxz * Mxy) / (2 * DET);
		double radius = Math.sqrt(CenterX * CenterX + CenterY * CenterY + Mz
				+ 2 * xnew);
		if (Double.isNaN(radius)) {
			IJ.error("Fit Circle", "Points are collinear.");
			return;
		}

		CenterX = CenterX + meanx;
		CenterY = CenterY + meany;
		imp.killRoi();

		// messo imp.setRoi anzich� IJ.makeOval perch� permette di non mostrare
		// l'immagine
		imp.setRoi(new OvalRoi((int) Math.round(CenterX - radius), (int) Math
				.round(CenterY - radius), (int) Math.round(2 * radius),
				(int) Math.round(2 * radius)));

		// IJ.makeOval((int) Math.round(CenterX - radius),
		// (int) Math.round(CenterY - radius),
		// (int) Math.round(2 * radius), (int) Math.round(2 * radius));

	}

	/***
	 * Liang-Barsky function by Daniel White
	 * http://www.skytopia.com/project/articles/compsci/clipping.html .This
	 * function inputs 8 numbers, and outputs 4 new numbers (plus a boolean
	 * value to say whether the clipped line is drawn at all). //
	 * 
	 * @param edgeLeft
	 *            lato sinistro, coordinata minima x = 0
	 * @param edgeRight
	 *            lato destro, coordinata max x = width
	 * @param edgeBottom
	 *            lato inferiore, coordinata max y = height
	 * @param edgeTop
	 *            lato superiore, coordinata minima y = 0
	 * @param x0src
	 *            punto iniziale segmento
	 * @param y0src
	 *            punto iniziale segmento
	 * @param x1src
	 *            punto finale segmento
	 * @param y1src
	 *            punto finale segmento
	 * @return
	 */
	public static double[] liangBarsky(double edgeLeft, double edgeRight,
			double edgeBottom, double edgeTop, double x0src, double y0src,
			double x1src, double y1src) {

		double t0 = 0.0;
		double t1 = 1.0;
		double xdelta = x1src - x0src;
		double ydelta = y1src - y0src;
		double p = 0;
		double q = 0;
		double r = 0;
		double[] clips = new double[4];

		for (int edge = 0; edge < 4; edge++) { // Traverse through left, right,
												// bottom, top edges.
			if (edge == 0) {
				p = -xdelta;
				q = -(edgeLeft - x0src);
			}
			if (edge == 1) {
				p = xdelta;
				q = (edgeRight - x0src);
			}
			if (edge == 2) {
				p = -ydelta;
				q = -(edgeBottom - y0src);
			}
			if (edge == 3) {
				p = ydelta;
				q = (edgeTop - y0src);
			}
			r = q / p;
			if (p == 0 && q < 0) {
				IJ.log("null 001");
				return null; // Don't draw line at all. (parallel line outside)
			}
			if (p < 0) {
				if (r > t1) {
					IJ.log("null 002");
					return null; // Don't draw line at all.
				} else if (r > t0)
					t0 = r; // Line is clipped!
			} else if (p > 0) {
				if (r < t0) {
					IJ.log("null 003");
					return null; // Don't draw line at all.
				} else if (r < t1)
					t1 = r; // Line is clipped!
			}
		}

		double x0clip = x0src + t0 * xdelta;
		double y0clip = y0src + t0 * ydelta;
		double x1clip = x0src + t1 * xdelta;
		double y1clip = y0src + t1 * ydelta;

		clips[0] = x0clip;
		clips[1] = y0clip;
		clips[2] = x1clip;
		clips[3] = y1clip;

		return clips; // (clipped) line is drawn
	}

	/**
	 * 
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @return
	 */
	public static double[] fromPointsToEquLineExplicit(double x1, double y1,
			double x2, double y2) {
		// la formula esplicita � y = mx + b
		// in cui m � detta anche slope (pendenza) e b intercept (intercetta)
		// non pu� rappresentare rette verticali
		double[] out = new double[2];

		double m = (y2 - y1) / (x2 - x1);

		double b = y1 - m * x1;

		out[0] = m;
		out[1] = b;
		return out;
	}

	/**
	 * 
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @return
	 */
	public static double[] fromPointsToEquLineImplicit(double x1, double y1,
			double x2, double y2) {
		// la formula implicita � ax + by + c = 0
		double[] out = new double[3];

		double a = y1 - y2;
		double b = x2 - x1;
		double c = x1 * y2 - x2 * y1;

		out[0] = a;
		out[1] = b;
		out[2] = c;

		return out;
	}

	public static double[] crossing(double x0, double y0, double x1, double y1,
			double width, double height) {

		double[] out1 = fromPointsToEquLineImplicit(x0, y0, x1, y1);

		// in out1 ottengo i valori di a,b,c da sostituire nella equazione
		// implicita della retta, nella forma ax+by+c=0

		// determinazione dei crossing points, in questi punti io conosco la x,
		// per i lati verticali e la y per gli orizzontali

		double tolerance = 1e-6;
		double a = out1[0];
		double b = out1[1];
		double c = out1[2];

		double x;
		double y;

		double[] clippingPoints = new double[4];
		int count = 0;

		// lato superiore
		y = 0;
		x = -(b * y + c) / a;

		if (isBetween(x, 0, width, tolerance)) {
			if (count <= 2) {
				clippingPoints[count++] = x;
				clippingPoints[count++] = y;
			} else
				return null;
		}

		// lato inferiore
		y = height;
		x = -(b * y + c) / a;
		if (isBetween(x, 0, width, tolerance)) {
			if (count <= 2) {
				clippingPoints[count++] = x;
				clippingPoints[count++] = y;
			} else
				return null;
		}

		// lato sinistro
		x = 0;
		y = -(a * x + c) / b;
		if (isBetween(y, 0, height, tolerance)) {
			if (count <= 2) {
				clippingPoints[count++] = x;
				clippingPoints[count++] = y;
			} else
				return null;
		}

		// lato destro
		x = width;
		y = -(a * x + c) / b;
		if (isBetween(y, 0, height, tolerance)) {
			if (count <= 2) {
				clippingPoints[count++] = x;
				clippingPoints[count++] = y;
			} else
				return null;
		}
		return clippingPoints;
	}

	public static boolean isBetween(double x1, double bound1, double bound2,
			double tolerance) {
		if (bound1 < bound2) {
			return ((x1 >= (bound1 - tolerance)) && (x1 <= (bound2 + tolerance)));
		} else {
			return ((x1 >= (bound2 - tolerance)) && (x1 <= (bound1 + tolerance)));
		}
	}

	/**
	 * Test images extraction on a temporary directory test2.jar
	 * 
	 * @return path of temporarary directory
	 */
	public String findTestImages() {
		InputOutput io = new InputOutput();
		io.extractFromJAR(MyConst.TEST_FILE, "A001_testP10", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "A002_testP10", "./Test2/");
		// io.extractFromJAR(MyConst.TEST_FILE, "A001_testP10", "./Test2/");
		// io.extractFromJAR(MyConst.TEST_FILE, "A002_testP10", "./Test2/");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY)
				.getPath();
		return (home1);
	}

	/**
	 * esegue posizionamento e calcolo roi circolare sul fondo
	 * 
	 * @param xRoi
	 *            coordinata x roi
	 * @param yRoi
	 *            coordinata y roi
	 * @param imp
	 *            puntatore ImagePlus alla immagine
	 * @param bstep
	 *            funzionamento passo passo
	 * @return dati statistici
	 */
	public static ImageStatistics backCalcP10(int xRoi, int yRoi, int diaRoi,
			ImagePlus imp, boolean bstep, boolean circular, boolean selftest) {

		ImageStatistics stat = null;
		boolean redo = false;
		do {
			if (imp.isVisible())
				imp.getWindow().toFront();
			if (circular) {
				imp.setRoi(new OvalRoi(xRoi - diaRoi / 2, yRoi - diaRoi / 2,
						diaRoi, diaRoi));
				// imp.updateAndDraw();
			} else {
				imp.setRoi(xRoi - diaRoi / 2, yRoi - diaRoi / 2, diaRoi, diaRoi);
				// imp.updateAndDraw();
			}

			if (!selftest) {
				if (redo) {
					ButtonMessages
							.ModelessMsg(
									"ATTENZIONE segnale medio fondo =0 SPOSTARE LA ROI E PREMERE CONTINUA",
									"CONTINUA");

				}
			}
			stat = imp.getStatistics();
			if (stat.mean == 0)
				redo = true;
			else
				redo = false;
			if (bstep)
				ButtonMessages.ModelessMsg("Segnale medio =" + stat.mean,
						"CONTINUA");
		} while (redo);
		return stat;
	} // backCalcP10

}