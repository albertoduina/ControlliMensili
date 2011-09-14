package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageCanvas;
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
import ij.process.ByteProcessor;
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
import utils.InputOutput;
import utils.Msg;
import utils.MyConst;
import utils.MyFileLogger;
import utils.MyLog;
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

	// private boolean profiVert = false;
	/**
	 * @ TODO : vedere se possibile fare il profilo inclinato @ TODO : il modo
	 * di funzionamento che ho pensato è di trovare il punto sulla circonferenza
	 * utilizzando Find Maxima, con una noise tolerance di almeno 100 si
	 * potrebbe fare una routine che parte da un valore basso e lo modifica fino
	 * a che si ottiene un singolo punto. Resta il problema di definire il
	 * centro del fantoccio. L'approccio più sempilce potrebbe essere di fare
	 * posizionare una roi circolare grande quanto il fantoccio (dal FOV,
	 * matrice sigla della misura dovrei essere in grado di scegliere il
	 * diametro giusto per il fantoccio. Una volta definito il punto sul
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

		String path1 = "C:/Users/alberto/Repository/git/ControlliMensili/ControlliMensili/test2/0001";
		String path2 = "C:/Users/alberto/Repository/git/ControlliMensili/ControlliMensili/test2/HUSA_001testP3";
		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = true;
		boolean test = true;
		prepUnifor(path1, path2, autoArgs, autoCalled, step, verbose, test);
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
				new AboutBox()
						.about("Controllo Uniformità, con save UNCOMBINED e immagini circolari",
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
				boolean verticalProfile = false;
				boolean autoCalled = true;
				boolean verbose = true;
				boolean test = false;
				int sqX = 0;
				int sqY = 0;

				prepUnifor(path1, path2, autoArgs, autoCalled, step, verbose,
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

	public static void prepUnifor(String path1, String path2, String autoArgs,
			boolean autoCalled, boolean step, boolean verbose, boolean test) {

		ImagePlus imp0 = UtilAyv.openImageNoDisplay(path1, verbose);
		int height = imp0.getHeight();
		int width = imp0.getWidth();

		int diaFantoccio = 220;

		int[] roiData = readPreferences(width, height, diaFantoccio);

		boolean profile = true;

		mainUnifor(path1, path2, roiData, autoArgs, profile, autoCalled, step,
				verbose, test);

	}

	@SuppressWarnings("deprecation")
	public static ResultsTable mainUnifor(String path1, String path2,
			int[] roiData, String autoArgs, boolean verticalProfile,
			boolean autoCalled, boolean step, boolean verbose, boolean test) {

		IJ.log("path1=" + path1 + " path2=" + path2);
		IJ.log("roiData[0]=" + roiData[0] + " roiData[1]=" + roiData[1]
				+ " roiData[2]=" + roiData[2]);
		IJ.log("autoArgs=" + autoArgs + " verticalProfile=" + verticalProfile
				+ " autoCalled=" + autoCalled);
		IJ.log("step=" + step + " verbose=" + verbose + " test=" + test);

		boolean accetta = false;
		ResultsTable rt = null;

		UtilAyv.setMeasure(MEAN + STD_DEV);

		do {
			ImagePlus imp1 = UtilAyv.openImageNormal(path1);
			ImagePlus imp2 = UtilAyv.openImageNoDisplay(path2, true);

			double dimPixel = ReadDicom.readDouble(ReadDicom.readSubstring(
					ReadDicom.readDicomParameter(imp1,
							MyConst.DICOM_PIXEL_SPACING), 1));

			// qui come prima cosa faccio posizionare la ROI
			int width = imp1.getWidth();
			int height = imp1.getHeight();

			int sqX = roiData[0];
			int sqY = roiData[1];
			int diamRoi1 = roiData[2];

			imp1.setRoi(new OvalRoi(sqX, sqY, diamRoi1, diamRoi1));
			imp1.updateAndDraw();
			new WaitForUserDialog("Posizionare la ROI e premere OK").show();

			// Rectangle boundRec = imp1.getProcessor().getRoi().getBounds();
			Rectangle boundRec = imp1.getProcessor().getRoi();

			IJ.log("boundRec.x=" + boundRec.x);
			IJ.log("boundRec.y=" + boundRec.y);
			IJ.log("boundRec.width=" + boundRec.width);
			IJ.log("boundRec.height=" + boundRec.height);
			IJ.log("boundRec.getCenterX=" + boundRec.getCenterX());
			IJ.log("boundRec.getCenterY=" + boundRec.getCenterY());

			int x1 = boundRec.x + boundRec.width / 2;
			int y1 = boundRec.y + boundRec.height / 2;
			diamRoi1 = boundRec.width;

			IJ.log("Il centro cerchio è x=" + x1 + " y=" + y1);

			ImageCanvas ic1 = imp1.getCanvas();
			Overlay over1 = new Overlay();
			imp1.setRoi(new OvalRoi(x1 + 4, y1 + 4, 8, 8));
			Roi roi1 = imp1.getRoi();
			if (roi1 == null)
				IJ.log("roi1==null");
			IJ.log("roi1=" + roi1);
			over1.addElement(roi1);
			over1.setFillColor(Color.red);
			imp1.killRoi();

			ic1.repaint();

			ImageProcessor ip1 = imp1.getProcessor();

			// --------------------------------
			// PASSO 1
			//
			// disegno MROI su imp1
			// --------------------------------

			// qui inizio a fare prove per il posizionamento automatico. Il
			// centro della ROI predeterminata lo conosco già, ora si tratta
			// di determinare il punto di MAXIMA

			double tolerance = 300.0;
			double threshold = 0.0;
			int outputType = MaximumFinder.LIST;
			boolean excludeOnEdges = false;
			boolean isEDM = false;

			MaximumFinder maxFinder = new MaximumFinder();

			double[] rx = null;
			double[] ry = null;
			ResultsTable rt1 = ResultsTable.getResultsTable();
			do {
				rt1.reset();
				maxFinder.findMaxima(ip1, tolerance, threshold, outputType,
						excludeOnEdges, isEDM);
				rx = rt1.getColumnAsDoubles(0);
				ry = rt1.getColumnAsDoubles(1);
				tolerance += 10.0;
			} while (rx.length > 1);

			IJ.log("tolerance=" + tolerance);
			imp1.setRoi(new OvalRoi((int) rx[0] + 4, (int) ry[0] + 4, 8, 8));
			over1.add(imp1.getRoi());
			imp1.killRoi();
			IJ.log("Il maxima trovato è x=" + rx[0] * dimPixel + " y=" + ry[0]
					* dimPixel);

			double aux1 = 0;
			aux1 = (double) x1 * dimPixel;

			int xStartReflineScreen = (int) aux1;
			aux1 = (double) y1 * dimPixel;
			int yStartReflineScreen = (int) aux1;

			IJ.log("Il centro cerchio SCREEN è x=" + xStartReflineScreen
					+ " y=" + yStartReflineScreen);
			aux1 = rx[0] * dimPixel;
			int xEndReflineScreen = (int) aux1;

			aux1 = ry[0] * dimPixel;
			int yEndReflineScreen = (int) aux1;

			IJ.log("Il maxima trovato SCREEN  è x=" + xEndReflineScreen + " y="
					+ yEndReflineScreen);

			imp1.setRoi(new Line(x1, y1, (int) rx[0], (int) ry[0]));
			imp1.updateAndDraw();
			new WaitForUserDialog("Verificare direzione e premere  OK").show();

			double prof = 30;

			double[] out1 = interpola((double) x1, (double) y1, rx[0], ry[0],
					prof);
			
			
			double ax= out1[0]*dimPixel;
			double ay= out1[1]*dimPixel;
			

			imp1.setRoi((int) ax, (int) ay, 30, 30);
			imp1.updateAndDraw();
			new WaitForUserDialog("Verificare ROI e premere  OK").show();

			// rt1.show("Results");
			// IJ.log("pippero="+pippero);

			// int[][] result1 = find255(bp1);
			// MyLog.logMatrix(result1, "result1");

			// IJ.run(imp1, "Find Maxima...",
			// "noise=10 output=[Point Selection]");
			new WaitForUserDialog("004 Do something, then click OK.").show();

			int posX = ReadDicom.readInt(Prefs.get("prefer.p10rmnPosX",
					Integer.toString(height / 2)));
			if ((posX < 10) || (posX > (height - 10)))
				posX = height / 2;
			int posY = ReadDicom.readInt(Prefs.get("prefer.p10rmnPosY",
					Integer.toString(width / 2)));
			if ((posY < 10) || (posY > (width - 10)))
				posY = width / 2;
			// UtilAyv.autoAdjust(imp1, ip1);
			if (imp1.isVisible())
				UtilAyv.backgroundEnhancement(0, 0, 10, imp1);
			int userSelection1 = 0;
			do {
				// imp1.setRoi(posX, posY, sqNEA, sqNEA);
				// imp1.updateAndDraw();
				// userSelection1 = menuPositionMroi();
			} while (userSelection1 == 1);
			//
			// rilettura posizione user-defined
			//
			// new WaitForUserDialog("Do something, then click OK.").show();

			ip1 = imp1.getProcessor();
			Rectangle boundingRectangle1 = ip1.getRoi();

			// writeStoredRoiData(boundingRectangle);
			int diamRoi2 = (int) (boundingRectangle1.width * MyConst.P10_AREA);

			int xRoi2 = boundingRectangle1.x
					+ ((boundingRectangle1.width - diamRoi2) / 2);
			int yRoi2 = boundingRectangle1.y
					+ ((boundingRectangle1.height - diamRoi2) / 2);

			imp1.setRoi(new OvalRoi(xRoi2, yRoi2, diamRoi2, diamRoi2));

			imp1.getWindow().toFront();
			sqX = boundingRectangle1.x;
			sqY = boundingRectangle1.y;

			// Prefs.set("prefer.p10rmnPosX",
			// Integer.toString(boundingRectangle.x));
			// Prefs.set("prefer.p10rmnPosY",
			// Integer.toString(boundingRectangle.y));

			new WaitForUserDialog("STOP, then click OK.").show();

			/**
			 * // // posiziono la ROI 7x7 all'interno di MROI // int gap =
			 * (sqNEA - MyConst.P5_MROI_7X7_PIXEL) / 2; imp1.setRoi(sqX + gap,
			 * sqY + gap, MyConst.P5_MROI_7X7_PIXEL, MyConst.P5_MROI_7X7_PIXEL);
			 * imp1.updateAndDraw(); ImageStatistics stat1 =
			 * imp1.getStatistics(); double signal1 = stat1.mean;
			 * 
			 * int xFondo = MyConst.P5_X_ROI_BACKGROUND; int yFondo =
			 * MyConst.P5_Y_ROI_BACKGROUND; if (test) yFondo = yFondo + 40; if
			 * (step) msgMroi(); // // disegno RoiFondo su imp1 //
			 * ImageStatistics statFondo = UtilAyv.backCalc(xFondo, yFondo,
			 * MyConst.P5_DIAM_ROI_BACKGROUND, imp1, step, false, test);
			 * 
			 * // // disegno MROI su imaDiff //
			 * 
			 * imaDiff.resetDisplayRange(); imaDiff.setRoi(sqX + gap, sqY + gap,
			 * MyConst.P5_MROI_7X7_PIXEL, MyConst.P5_MROI_7X7_PIXEL);
			 * imaDiff.updateAndDraw(); ImageStatistics statImaDiff =
			 * imaDiff.getStatistics(); imaDiff.updateAndDraw(); if
			 * (imaDiff.isVisible()) imaDiff.getWindow().toFront();
			 * 
			 * if (step) msgMroi(); // // calcolo P su imaDiff // double
			 * prelimImageNoiseEstimate_MROI = statImaDiff.stdDev /
			 * Math.sqrt(2);
			 * 
			 * if (step) { msgNea(prelimImageNoiseEstimate_MROI); } // // loop
			 * di calcolo NEA su imp1 // imp1.setRoi(sqX, sqY, sqNEA, sqNEA);
			 * imp1.updateAndDraw(); if (imp1.isVisible())
			 * imp1.getWindow().toFront(); // // qui, se il numero dei pixel <
			 * 121 dovrò incrementare sqR2 e // ripetere il loop // double
			 * checkPixels = MyConst.P5_CHECK_PIXEL_MULTIPLICATOR
			 * prelimImageNoiseEstimate_MROI; int area11x11 =
			 * MyConst.P5_NEA_11X11_PIXEL MyConst.P5_NEA_11X11_PIXEL; int
			 * enlarge = 0; int pixx = 0;
			 * 
			 * do { pixx = countPix(imp1, sqX - enlarge, sqY - enlarge, sqNEA,
			 * checkPixels);
			 * 
			 * imp1.setRoi(sqX - enlarge, sqY - enlarge, sqNEA, sqNEA);
			 * imp1.updateAndDraw(); // imp1.getWindow().toFront(); if (step)
			 * msgDisplayNEA();
			 * 
			 * if (pixx < area11x11) { sqNEA = sqNEA + 2; // accrescimento area
			 * enlarge = enlarge + 1; } if (step) { msgEnlargeRoi(sqNEA); }
			 * 
			 * // verifico che quando cresce il lato del quadrato non si esca //
			 * dall'immagine
			 * 
			 * if ((sqX + sqNEA - enlarge) >= width || (sqX - enlarge) <= 0) {
			 * msgNot121(); return null; } if ((sqY + sqNEA - enlarge) >= height
			 * || (sqY - enlarge) <= 0) { msgNot121(); return null; } if (step
			 * && pixx >= area11x11) msgSqr2OK(pixx);
			 * 
			 * } while (pixx < area11x11);
			 * 
			 * imp1.setRoi(sqX - enlarge, sqY - enlarge, sqNEA, sqNEA);
			 * imp1.updateAndDraw(); if (imp1.isVisible())
			 * imp1.getWindow().toFront(); // // calcolo SD su imaDiff quando i
			 * corrispondenti pixel // di imp1 passano il test // double[] out1
			 * = devStandardNema(imp1, imaDiff, sqX - enlarge, sqY - enlarge,
			 * sqNEA, checkPixels); if (step) msgDisplayMean4(out1[0], out1[1]);
			 * // // calcolo SNR finale // double snr = signal1 / (out1[1] /
			 * Math.sqrt(2)); if (step) msgSnr(snr);
			 * 
			 * // // calcolo simulata // int[][] classiSimulata =
			 * generaSimulata(sqX + gap, sqY + gap, MyConst.P5_MROI_7X7_PIXEL,
			 * imp1, step, verbose, test); // // calcolo posizione fwhm a metà
			 * della MROI // if (imp1.isVisible()) imp1.getWindow().toFront();
			 * // // 28feb05 qui dò la possibilità di modificare la posizione su
			 * cui // verrà calcolato l'FWHM // int xStartProfile = 0; int
			 * yStartProfile = 0; int xEndProfile = 0; int yEndProfile = 0;
			 * 
			 * if (test) { if (verticalProfile) { xStartProfile = sqX + gap +
			 * MyConst.P5_MROI_7X7_PIXEL / 2; yStartProfile = 1; xEndProfile =
			 * xStartProfile; yEndProfile = height; } else { xStartProfile = 1;
			 * yStartProfile = sqY + gap + MyConst.P5_MROI_7X7_PIXEL / 2;
			 * xEndProfile = width; yEndProfile = yStartProfile; } } else { Line
			 * line = selectProfilePosition(sqX + gap, sqY + gap,
			 * MyConst.P5_MROI_7X7_PIXEL, imp1);
			 * 
			 * xStartProfile = line.x1; yStartProfile = line.y1; xEndProfile =
			 * line.x2; yEndProfile = line.y2;
			 * 
			 * }
			 * 
			 * double[] outFwhm2 = analyzeProfile(imp1, xStartProfile,
			 * yStartProfile, xEndProfile, yEndProfile, dimPixel, step);
			 * 
			 * // // Salvataggio dei risultati nella ResultsTable
			 * 
			 * // String[][] tabCodici = new InputOutput().readFile1( //
			 * MyConst.CODE_FILE, MyConst.TOKENS4);
			 * 
			 * String[][] tabCodici = TableCode.loadTable(MyConst.CODE_FILE);
			 * 
			 * // String[] info1 = //
			 * ReportStandardInfo.getSimpleStandardInfo(path[0], // imp1,
			 * tabCodici, VERSION, autoCalled);
			 * 
			 * // // rt = ReportStandardInfo.putSimpleStandardInfoRT(info1); int
			 * col = 2; String t1 = "TESTO          "; rt.setHeading(++col,
			 * "roi_x"); rt.setHeading(++col, "roi_y"); rt.setHeading(++col,
			 * "roi_b"); rt.setHeading(++col, "roi_h");
			 * 
			 * rt.addLabel(t1, simulataName); rt.incrementCounter();
			 * 
			 * rt.addLabel(t1, "Segnale"); rt.addValue(2, signal1);
			 * rt.addValue(3, sqX); rt.addValue(4, sqY); rt.addValue(5, sqNEA);
			 * rt.addValue(6, sqNEA);
			 * 
			 * rt.incrementCounter(); rt.addLabel(t1, "Rumore_Fondo");
			 * rt.addValue(2, statFondo.mean); rt.addValue(3, statFondo.roiX);
			 * rt.addValue(4, statFondo.roiY); rt.addValue(5,
			 * statFondo.roiWidth); rt.addValue(6, statFondo.roiHeight);
			 * 
			 * rt.incrementCounter(); rt.addLabel(t1, "SnR"); rt.addValue(2,
			 * snr); rt.addValue(3, sqX); rt.addValue(4, sqY); rt.addValue(5,
			 * sqNEA); rt.addValue(6, sqNEA);
			 * 
			 * rt.incrementCounter(); rt.addLabel(t1, "FWHM"); rt.addValue(2,
			 * outFwhm2[0]); rt.addValue(3, xStartProfile); rt.addValue(4,
			 * yStartProfile); rt.addValue(5, xEndProfile); rt.addValue(6,
			 * yEndProfile);
			 * 
			 * String[] levelString = { "+20%", "+10%", "-10%", "-10%", "-30%",
			 * "-40%", "-50%", "-60%", "-70%", "-80%", "-90%", "fondo" };
			 * 
			 * for (int i1 = 0; i1 < classiSimulata.length; i1++) {
			 * rt.incrementCounter(); rt.addLabel(t1, ("Classe" +
			 * classiSimulata[i1][0]) + "_" + levelString[i1]); rt.addValue(2,
			 * classiSimulata[i1][1]); } if (verbose && !test)
			 * rt.show("Results");
			 * 
			 * if (autoCalled && !test) { accetta = Msg.accettaMenu(); } else {
			 * if (!test) { accetta = Msg.msgStandalone(); } else { accetta =
			 * test; }
			 * 
			 * }
			 */
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

				String autoArgs = "0";
				boolean autoCalled = false;
				boolean step = false;
				boolean verbose = true;
				boolean test = true;
				double[] vetReference = referenceGe();
				boolean verticalProfile = false;
				// ResultsTable rt1 = mainUnifor(path, sqX, sqY, autoArgs,
				// verticalProfile, autoCalled, step, verbose, test);
				// if (rt1 == null)
				// return;
				//
				// double[] vetResults = UtilAyv.vectorizeResults(rt1);
				// boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				// MyConst.P5_vetName);
				// if (ok)
				// Msg.msgTestPassed();
				// else
				// Msg.msgTestFault();
				// UtilAyv.afterWork();
				// break;
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
				// ResultsTable rt1 = mainUnifor(path, sqX, sqY, autoArgs,
				// verticalProfile, autoCalled, step, verbose, test);
				// if (rt1 == null)
				// return;
				// double[] vetResults = UtilAyv.vectorizeResults(rt1);
				// boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				// MyConst.P5_vetName);
				// if (ok)
				// Msg.msgTestPassed();
				// else
				// Msg.msgTestFault();
				// UtilAyv.afterWork();
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
		double backNoise = 7.87;
		double snRatio = 38.26155499878056;
		double fwhm = 36.56299427908097;
		double num1 = 1484.0;
		double num2 = 525.0;
		double num3 = 1188.0;
		double num4 = 681.0;
		double num5 = 769.0;
		double num6 = 882.0;
		double num7 = 1077.0;
		double num8 = 1319.0;
		double num9 = 1787.0;
		double num10 = 2581.0;
		double num11 = 5238.0;
		double num12 = 48005.0;

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
		double backNoise = 84.06;
		double snRatio = 93.19892843975435;
		double fwhm = 32.93099254935456;
		double num1 = 1914.0;
		double num2 = 479.0;
		double num3 = 1316.0;
		double num4 = 938.0;
		double num5 = 1271.0;
		double num6 = 1957.0;
		double num7 = 3346.0;
		double num8 = 2681.0;
		double num9 = 2333.0;
		double num10 = 2345.0;
		double num11 = 1118.0;
		double num12 = 45838.0;

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

		String autoArgs = "-1";
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = true;
		int sqX = MyConst.P5_X_ROI_TESTSIEMENS;
		int sqY = MyConst.P5_Y_ROI_TESTSIEMENS;
		boolean verticalProfile = true;

		// ResultsTable rt1 = p10rmn_.mainUnifor(path, sqX, sqY, autoArgs,
		// verticalProfile, autoCalled, step, verbose, test);
		// double[] vetResults = UtilAyv.vectorizeResults(rt1);
		// boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
		// MyConst.P5_vetName);
		// if (ok) {
		// IJ.log("Il test di p5rmn_ UNIFORMITA' SUPERFICIALE è stato SUPERATO");
		// } else {
		// IJ.log("Il test di p5rmn_ UNIFORMITA' SUPERFICIALE evidenzia degli ERRORI");
		// }
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
	 * Estrae la numerosità dell classi dalla simulata
	 * 
	 * @param imp1
	 *            immagine simulata da analizzare
	 * @return numerosità delle classi in cui per ogni elemento abbiamo
	 *         [valore][numerosità]
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
	 * @return numeriosità classi simulata
	 */
	private static int[][] generaSimulata(int xRoi, int yRoi, int diamRoi,
			ImagePlus imp, boolean step, boolean verbose, boolean test) {

		int xRoiSimulata = xRoi + (diamRoi - MyConst.P5_NEA_11X11_PIXEL) / 2;
		int yRoiSimulata = yRoi + (diamRoi - MyConst.P5_NEA_11X11_PIXEL) / 2;
		ImagePlus impSimulata = simulata12Classi(xRoiSimulata, yRoiSimulata,
				MyConst.P5_NEA_11X11_PIXEL, imp);
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
			ImagePlus imp1) {

		// partiamo da dove è stata posizionata la ROI

		int xStartProfile = 0;
		int yStartProfile = 0;
		int xEndProfile = 0;
		int yEndProfile = 0;
		boolean continua = false;

		int width = imp1.getWidth();
		int height = imp1.getHeight();
		// per la direzione del profilo utilizziamo l'ultima selezionata
		boolean profiVert = Prefs.get("prefer.p5rmnVert", true);
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

	public static void overlayCircles(ImagePlus imp1, int gridNumber,
			boolean verbose) {

		int elements = 10;
		CustomCanvasGeneric ccg1 = new CustomCanvasGeneric(imp1);
		ccg1.setCircleElements(elements);
		if (verbose) {
			ImageWindow iw1 = new ImageWindow(imp1, ccg1);
			iw1.maximize();
		}
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

	public static double[] interpola(double ax, double ay, double bx,
			double by, double prof) {

		double alf1 = Math.atan((ay - by) / (ax - bx));
		IJ.log("-------- interpola --------");
		IJ.log("angolo= " + Math.toDegrees(alf1) + "  sin= " + Math.sin(alf1));
		IJ.log("angolo= " + Math.toDegrees(alf1) + "  cos= " + Math.cos(alf1));
		double deltaX = prof / Math.cos(alf1);
		double deltaY = prof / Math.sin(alf1);
		IJ.log("deltaX=" + deltaX);
		IJ.log("deltaY=" + deltaY);
		double cx = ax + deltaX;
		double cy = ay + deltaY;
		IJ.log("cx=" + cx);
		IJ.log("cy=" + cy);
		double[] out = new double[2];
		out[0] = cx;
		out[1] = cy;
		IJ.log("-----------------------------");
		return out;
	}

} // p5rmn_
