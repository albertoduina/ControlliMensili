package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.Line;
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
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyCircleDetector;
import utils.MyFilter;
import utils.MyLine;
import utils.MyMsg;
import utils.MyConst;
import utils.MyFwhm;
import utils.MyLog;
import utils.MyPlot;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReadVersion;
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

	public static String VERSION = "p10_rmn_v1.10_13oct11_";

	private String TYPE = " >> CONTROLLO SUPERFICIALI UNCOMBINED_";

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.txt
	 */
	private static String fileDir = "";

	private static String simulataName = "";
	private static boolean previous = false;
	private static boolean init1 = true;
	@SuppressWarnings("unused")
	private static boolean pulse = false; // lasciare, serve anche se segnalato
											// inutilizzato
	private static final boolean debug = true;

	public void run(String args) {

		String className = this.getClass().getName();

		// VERSION = className + "_build_"
		// + ReadVersion.readVersionInfoInManifest("contMensili")
		// + "_iw2ayv_build_"
		// + ReadVersion.readVersionInfoInManifest("utils");

		VERSION = className + "_build_" + MyVersion.getVersion()
				+ "_iw2ayv_build_" + MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");

		if (IJ.versionLessThan("1.43k"))
			return;

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}

		return;
	}

	/***
	 * MODI DI FUNZIONAMENTO:
	 * 
	 * FAST: NESSUNA IMMAGINE A DISPLAY, NESSUNA INTERAZIONE CON L'UTENTE
	 * 
	 * FAST NON A BUON FINE: VIENE MOSTRATA L'IMMAGINE, SI CHIEDE IL
	 * POSIZIONAMENTO DEL CERCHIO CHE IDENTIFICA LA POSIZIONE DEL FANTOCCIO,
	 * VIENE MOSTRATO L'OVERLAY COMPLETO DELLA ROI QUADRATA E CHIESTA CONFERMA
	 * ALL'OPERATORE (TENERE VIA UN SET DI IMMAGINI, IN MODO DA POTER ESEGUIRE
	 * ANCHE I TEST DI REGRESSIONE)
	 * 
	 * MODO NORMALE: VIENE MOSTRATA L'IMMAGINE CON L'OVERLAY COMPLETO DELLA ROI
	 * QUADRATA, CHIESTA CONFERMA ALL'OPERATORE
	 * 
	 * STEP: VIENE MOSTRATO IL FUNZIONAMENTO PASSO PER PASSO, DI TUTTE LE MICRO
	 * OPERAZIONI
	 * 
	 * PROVA: VIEME MOSTRATO COME PER IL MODO NORMALE MA SU IMMAGINE CAMPIONE
	 * 
	 * TEST SILENT: VIENE ESEGUITO IL MODO FAST SULLA IMMAGINE CAMPIONE
	 * 
	 * 
	 * ****************** QUANTO SCRITTO DI SEGUITO NON E'VALIDO *********** A
	 * QUESTA DESCRIZIONE VA AGGIUNTO IL MODO DI FUNZIONAMENTO SE ANCHE UNO SOLO
	 * DEI PARAMETRI VA AL DI FUORI DAI LIMITI DI MASSIMA:
	 * 
	 * VIENE PRESENTATO UN MESSAGGIO CHE INDICA CHE VALORE SUPERA I LIMITI, CI
	 * SONO 3 POSSIBILITA' DI SCELTA: CONTINUA, VISUALIZZA, SUCCESSIVA CONTINUA
	 * PERMETTE DI CONTINUARE, COME SE NON CI FOSSE STATO ALCUN SUPERAMENTO,
	 * VISUALIZZA LO SI UTILIZZA SE SI E' IN MODO FAST, RIPARTE CON IL
	 * CONTROLLO, PERO'LE IMMAGINI VENGONO VISUALIZZATE, PERMETTENDO DI
	 * LOCALOIZZARE (FORSE) IL PROBLEMA, SUCCESSIVA PASSA ALLA IMMAGINE
	 * SUCCESSIVA, I RISULATATI NON VERRANNO SCRITTI
	 * 
	 * 
	 * 
	 */

	/**
	 * Menu funzionamento manuale (chiamato dal menu di ImageJ)
	 * 
	 * @param preset
	 *            da utilizzare per eventuali test
	 * @param testDirectory
	 *            da utilizzare per eventuali test
	 * @return
	 */

	public int manualMenu(int preset, String testDirectory) {
		boolean retry = false;
		boolean step = false;
		boolean fast = false;
		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox()
				// .about("Controllo Uniformità, con save UNCOMBINED e immagini circolari",
				// this.getClass());
				new AboutBox()
						.about("Controllo Uniformità, con save UNCOMBINED e immagini circolari",
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
				double profond = 30;
				boolean silent = false;
				mainUnifor(path1, path2, "0", profond, "", autoCalled, step,
						verbose, test, fast, silent);

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
	 * Menu funzionamento automatico (chiamato da Sequenze)
	 * 
	 * @param autoArgs
	 *            parametri ricevuti da Sequenze ad esempio: "#2#3"
	 * @return
	 */

	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p10 riceve " + autoArgs);

		boolean fast = Prefs.get("prefer.fast", "false").equals("true") ? true
				: false;
		// IJ.log("p10rmn_.autoMenu fast= " + fast);
		// IJ.log("p10rmn_.autoMenu autoargs= " + autoArgs);
		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p10rmn_");
			selfTestSilent();
			return 0;
		}

		if ((nTokens != MyConst.TOKENS2) && (nTokens != MyConst.TOKENS4)) {
			MyMsg.msgParamError();
			return 0;
		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);

		String info10 = (vetRiga[0] + 1) + " / "
				+ TableSequence.getLength(iw2ayvTable) + "   code= "
				+ TableSequence.getCode(iw2ayvTable, vetRiga[0]) + "   coil= "
				+ TableSequence.getCoil(iw2ayvTable, vetRiga[0]);

		String path1 = "";
		String path2 = "";
		// TableUtils.dumpTableRow(iw2ayvTable, vetRiga[0]);

		if (nTokens == MyConst.TOKENS2) {
			UtilAyv.checkImages(vetRiga, iw2ayvTable, 2, debug);

			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			MyLog.logDebug(vetRiga[0], "P10", fileDir);
			MyLog.logDebug(vetRiga[1], "P10", fileDir);
		} else {
			UtilAyv.checkImages(vetRiga, iw2ayvTable, 3, debug);

			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
			MyLog.logDebug(vetRiga[0], "P10", fileDir);
			MyLog.logDebug(vetRiga[2], "P10", fileDir);
		}

		boolean step = false;
		boolean retry = false;
		double profond = readDouble(TableSequence.getProfond(iw2ayvTable,
				vetRiga[0]));
		if (UtilAyv.isNaN(profond)) {
			MyLog.logVector(iw2ayvTable[vetRiga[0]], "stringa");
			MyLog.waitHere();
		}

		if (fast) {
			retry = false;
			boolean autoCalled = true;
			boolean verbose = false;
			boolean test = false;
			boolean silent = false;
			// MyLog.waitHere(TableSequence.getCode(iw2ayvTable, vetRiga[0])
			// + "   " + TableSequence.getCoil(iw2ayvTable, vetRiga[0])
			// + "   " + (vetRiga[0] + 1) + " / "
			// + TableSequence.getLength(iw2ayvTable));

			mainUnifor(path1, path2, autoArgs, profond, info10, autoCalled,
					step, verbose, test, fast, silent);

			UtilAyv.saveResults3(vetRiga, fileDir, iw2ayvTable);

			UtilAyv.afterWork();

		} else
			do {
				int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]),
						TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

				switch (userSelection1) {
				case ABORT:
					new AboutBox().close();
					return 0;
				case 2:
					// new AboutBox()
					// .about("Controllo Bobine Array, immagine circolare UNCOMBINED",
					// this.getClass());
					new AboutBox()
							.about("Controllo Bobine Array, immagine circolare UNCOMBINED",
									MyVersion.CURRENT_VERSION);
					retry = true;
					break;
				case 3:
					step = true;
				case 4:
					retry = false;
					boolean autoCalled = true;
					boolean verbose = true;
					boolean test = false;
					boolean silent = false;

					// double profond = Double.parseDouble(TableSequence
					// .getProfond(iw2ayvTable, vetRiga[0]));

					mainUnifor(path1, path2, autoArgs, profond, info10,
							autoCalled, step, verbose, test, fast, silent);

					UtilAyv.saveResults3(vetRiga, fileDir, iw2ayvTable);

					UtilAyv.afterWork();
					break;
				}
			} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	/**
	 * Main per il calcolo dell'uniformità per bobine di superficie
	 * 
	 * @param path1
	 *            path prima immagine
	 * @param path2
	 *            path seconda immagine
	 * @param autoArgs
	 *            argomentoi ricevuti dalla chiamata
	 * @param profond
	 *            profondità a cui porre la ROI
	 * @param info10
	 * @param autoCalled
	 *            flag true se chiamato in automatico
	 * @param step
	 *            flag true se funzionamento passo-passo
	 * @param verbose
	 *            flag true se verbose
	 * @param test
	 *            flag true se in modo test
	 * @param fast
	 *            flag true se in modo batch
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public static ResultsTable mainUnifor(String path1, String path2,
			String autoArgs, double profond, String info10, boolean autoCalled,
			boolean step, boolean verbose, boolean test, boolean fast,
			boolean silent) {

		boolean accetta = false;
		boolean abort = false;
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		double angle = Double.NaN;

		//
		// IL VERBOSE FORZATO A TRUE, QUI DI SEGUITO, SERVE
		// A MOSTRARE, PER UN ISTANTE, DOVE VIENE POSIZIONATA AUTOMATICAMENTE
		// LA ROI, anche in FAST.
		//

		verbose = true;
		String[][] limiti = new InputOutput().readFile6("LIMITI.csv");
		double[] vetMinimi = UtilAyv.doubleLimiti(UtilAyv.decoderLimiti(limiti,
				"P10MIN"));
		double[] vetMaximi = UtilAyv.doubleLimiti(UtilAyv.decoderLimiti(limiti,
				"P10MAX"));

		// ===============================================================
		// ATTENZIONE: questi sono solo valori di default utilizzati in
		// assenza di limiti.csv
		// ================================================================
		double minMean7x7 = +10;
		double maxMean7x7 = +4096;
		double minMeanBkg = -2048;
		double maxMeanBkg = +2048;
		double minSnRatio = +10;
		double maxSnRatio = +800;

		double minFWHM = 0;
		double maxFWHM = +512;
		double minUiPerc = +5;
		double maxUiPerc = +100;
		double minFitError = +0;
		double maxFitError = +20;
		// ================================================================
		if (vetMinimi == null) {
			MyLog.waitHere(listaMessaggi(65), debug);
		} else {
			minMean7x7 = vetMinimi[0];
			minMeanBkg = vetMinimi[1];
			minSnRatio = vetMinimi[2];
			minFWHM = vetMinimi[3];
			minUiPerc = vetMinimi[4];
			minFitError = vetMinimi[5];
		}
		if (vetMaximi == null) {
			MyLog.waitHere(listaMessaggi(66), debug);
		} else {
			maxMean7x7 = vetMaximi[0];
			maxMeanBkg = vetMaximi[1];
			maxSnRatio = vetMaximi[2];
			maxFWHM = vetMaximi[3];
			maxUiPerc = vetMaximi[4];
			maxFitError = vetMaximi[5];
		}

		//
		//
		//
		// String[][] limiti = new InputOutput().readFile7("limiti.csv");
		// if (limiti == null)
		// MyLog.waitHere("limiti == null");
		// double[] vetMinimi = doubleLimiti(decoderLimiti(limiti, "P10MIN"));
		// double[] vetMaximi = doubleLimiti(decoderLimiti(limiti, "P10MAX"));
		do {
			ImagePlus imp11 = null;
			if (fast || silent)
				imp11 = UtilAyv.openImageNoDisplay(path1, true);
			else
				imp11 = UtilAyv.openImageMaximized(path1);

			if (imp11 == null)
				MyLog.waitHere("Non trovato il file " + path1);
			// ImageWindow iw11 = WindowManager.getCurrentWindow();
			double out2[] = positionSearch(imp11, profond, info10, autoCalled,
					step, verbose, test, fast);
			if (out2 == null) {
				MyLog.waitHere("out2==null");
				return null;
			}

			if (imp11.isVisible())
				imp11.getWindow().close();

			// MyLog.logVector(out2, "out2");
			// MyLog.waitHere("FINE POSITION SEARCH");
			// ========================================================================
			// se non ho trovato la posizione mi ritrovo qui senza out2[]
			// valido
			//
			// se anche uno solo dei check limits è fallito si deve tornare qui
			// ed eseguire il controllo, come minimo senza fast attivo ed in
			// modalità verbose
			// ========================================================================
			ImagePlus imp1 = null;
			ImagePlus imp2 = null;
			ImageWindow iw1 = null;
			if (verbose && !silent) {
				imp1 = UtilAyv.openImageMaximized(path1);
				iw1 = WindowManager.getCurrentWindow();
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			} else {
				imp1 = UtilAyv.openImageNoDisplay(path1, true);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			}
			if (imp2 == null)
				MyLog.waitHere("Non trovato il file " + path2);
			// ImageWindow iw1=WindowManager.getCurrentWindow();

			// ============================================================================
			// Fine calcoli geometrici
			// Inizio calcoli Uniformità
			// ============================================================================

			Overlay over2 = new Overlay();
			Overlay over3 = new Overlay();

			int sqNEA = MyConst.P10_NEA_11X11_PIXEL;
			// disegno MROI già predeterminata
			imp1.setOverlay(over2);
			over2.setStrokeColor(Color.red);
			int xCenterRoi = (int) out2[0];
			int yCenterRoi = (int) out2[1];
			int xCenterCircle = (int) out2[2];
			int yCenterCircle = (int) out2[3];
			int xMaxima = (int) out2[4];
			int yMaxima = (int) out2[5];
			angle = out2[6];
			int xBordo = (int) out2[7];
			int yBordo = (int) out2[8];

			int width = imp1.getWidth();
			int height = imp1.getHeight();
			double dimPixel = ReadDicom.readDouble(ReadDicom.readSubstring(
					ReadDicom.readDicomParameter(imp1,
							MyConst.DICOM_PIXEL_SPACING), 1));

			if (verbose) {
				// =================================================
				// Mroi
				// imp1.setRoi(xCenterRoi - 10, yCenterRoi - 10, 20, 20);
				// imp1.getRoi().setStrokeColor(Color.red);
				// over2.addElement(imp1.getRoi());
				// imp1.killRoi();
				// Centro cerchio
				MyCircleDetector.drawCenter(imp1, over2, xCenterCircle,
						yCenterCircle, Color.red);

				// imp1.killRoi();

				MyCircleDetector.drawCenter(imp1, over2, xMaxima, yMaxima,
						Color.green);

				MyCircleDetector.drawCenter(imp1, over2, xBordo, yBordo,
						Color.pink);

				// imp1.killRoi();

				imp1.setRoi(new Line(xCenterCircle, yCenterCircle, xBordo,
						yBordo));
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.green);
				imp1.updateAndDraw();

				// =================================================
			}

			imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA,
					sqNEA);

			imp1.getRoi().setStrokeColor(Color.green);
			imp1.getRoi().setStrokeWidth(1.1);
			over2.addElement(imp1.getRoi());
			imp1.updateAndDraw();

			// MyLog.waitHere("Disegnata MROI xCenterRoi = " + xCenterRoi
			// + " yCenterRoi= " + yCenterRoi);
			if (step)
				MyLog.waitHere(listaMessaggi(30), debug);
			//
			// posiziono la ROI 7x7 all'interno di MROI
			//
			int sq7 = MyConst.P10_MROI_7X7_PIXEL;
			imp1.setRoi(xCenterRoi - sq7 / 2, yCenterRoi - sq7 / 2, sq7, sq7);
			if (verbose) {
				imp1.getRoi().setStrokeColor(Color.red);
				imp1.getRoi().setStrokeWidth(1.1);
				over2.addElement(imp1.getRoi());
			}
			imp1.updateAndDraw();
			if (step)
				MyLog.waitHere(listaMessaggi(31), debug);

			ImageStatistics stat7x7 = imp1.getStatistics();

			// =============================================================
			// int xFondo = MyConst.P10_X_ROI_BACKGROUND;
			// int yFondo = MyConst.P10_Y_ROI_BACKGROUND;
			// int dFondo = MyConst.P10_DIAM_ROI_BACKGROUND;

			double xBkg = imp1.getWidth() - MyConst.P10_X_ROI_BACKGROUND;
			double yBkg = MyConst.P10_Y_ROI_BACKGROUND;
			boolean irraggiungibile = true;
			int diamBkg = MyConst.P10_DIAM_ROI_BACKGROUND;
			int guard = 10;
			boolean demo = !fast;
			boolean circle = true;
			int mode = 1;

			// MyLog.waitHere("step= " + step + " verbose= " + verbose +
			// " test= "
			// + test + " fast= " + fast + " silent= " + silent);

			double[] backPos = UtilAyv.positionSearch15(imp1, out2, xBkg, yBkg,
					diamBkg, guard, mode, info10, circle, autoCalled, step,
					demo, test, fast, irraggiungibile);

			// public static int[] positionSearch15(ImagePlus imp1, int[]
			// circleData,
			// int xBkg, int yBkg, int diamBkg, int guard, int mode, String
			// info1,
			// boolean circle, boolean autoCalled, boolean step, boolean demo,
			// boolean test, boolean fast, boolean irraggiungibile) {

			xBkg = backPos[0] - diamBkg / 2;
			yBkg = backPos[1] - diamBkg / 2;

			//
			// disegno RoiFondo su imp1
			//
			Boolean circular = true;
			ImageStatistics statBkg = UtilAyv.backCalc2((int) xBkg, (int) yBkg,
					(int) diamBkg, imp1, step, circular, test);
			if (step)
				MyLog.waitHere(listaMessaggi(26) + statBkg.mean, debug);

			//
			// =================================================
			over2.addElement(imp1.getRoi());
			imp1.updateAndDraw();
			ImagePlus imp8 = imp1.flatten();
			String newName = path1 + "_flat_p10.jpg";
			new FileSaver(imp8).saveAsJpeg(newName);
			// =============================================================

			//
			// disegno MROI su imaDiff
			//
			ImagePlus imaDiff = UtilAyv.genImaDifference(imp1, imp2);
			ImageWindow iwDiff = null;
			if (verbose && !fast) {
				UtilAyv.showImageMaximized(imaDiff);
				iwDiff = WindowManager.getCurrentWindow();
			}

			imaDiff.setOverlay(over2);
			// imaDiff.setOverlay(over3);
			MyLog.waitHere("overlay 2 su imaDiff");

			if (verbose && !fast) {
				// =================================================
				imaDiff.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2,
						sqNEA, sqNEA);
				over3.addElement(imaDiff.getRoi());
				imaDiff.killRoi();
				imaDiff.setRoi(new OvalRoi(xCenterCircle - 4,
						yCenterCircle - 4, 8, 8));
				over3.addElement(imaDiff.getRoi());
				imaDiff.killRoi();
				imaDiff.setRoi(new OvalRoi(xMaxima - 4, yMaxima - 4, 8, 8));
				over3.addElement(imaDiff.getRoi());
				imaDiff.setRoi(new Line(xCenterCircle, yCenterCircle, xMaxima,
						yMaxima));
				over3.addElement(imaDiff.getRoi());
				over3.setStrokeColor(Color.green);
				imaDiff.updateAndDraw();

				// =================================================
			}

			imaDiff.resetDisplayRange();
			imaDiff.setRoi(xCenterRoi - sq7 / 2, yCenterRoi - sq7 / 2, sq7, sq7);
			imaDiff.updateAndDraw();
			ImageStatistics statImaDiff = imaDiff.getStatistics();

			imaDiff.updateAndDraw();
			ImageUtils.imageToFront(iwDiff);

			if (step)
				MyLog.waitHere(listaMessaggi(33), debug);

			ImageUtils.imageToFront(iw1);

			//
			// calcolo P su imaDiff
			//
			double prelimImageNoiseEstimate_MROI = statImaDiff.stdDev
					/ Math.sqrt(2);

			if (step)
				MyLog.waitHere(listaMessaggi(34) + "noise= "
						+ prelimImageNoiseEstimate_MROI, debug);

			//
			// loop di calcolo NEA su imp1
			//
			imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA,
					sqNEA);
			// if (imp1.isVisible())
			// imp1.getWindow().toFront();

			//
			// qui, se il numero dei pixel < 121 dovrò incrementare sqR2 e
			// ripetere il loop
			//

			double checkPixels = MyConst.P10_CHECK_PIXEL_MULTIPLICATOR
					* prelimImageNoiseEstimate_MROI;
			int area11x11 = MyConst.P10_NEA_11X11_PIXEL
					* MyConst.P10_NEA_11X11_PIXEL;
			int enlarge = 0;
			int pixx = 0;

			do {

				boolean paintPixels = false;

				pixx = countPixTest(imp1, xCenterRoi, yCenterRoi, sqNEA,
						checkPixels, paintPixels);

				imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2,
						sqNEA, sqNEA);
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.green);

				imp1.updateAndDraw();

				// imp1.getWindow().toFront();
				if (step)
					MyLog.waitHere(listaMessaggi(25));

				if (pixx < area11x11) {
					sqNEA = sqNEA + 2; // accrescimento area
					enlarge = enlarge + 1;
				}
				if (step)
					MyLog.waitHere(listaMessaggi(35) + sqNEA, debug);

				// verifico che quando cresce il lato del quadrato non si
				// esca
				// dall'immagine

				if ((xCenterRoi + sqNEA - enlarge) >= width
						|| (xCenterRoi - enlarge) <= 0) {
					MyLog.waitHere(listaMessaggi(32), debug);
					return null;
				}
				if ((yCenterRoi + sqNEA - enlarge) >= height
						|| (yCenterRoi - enlarge) <= 0) {
					MyLog.waitHere(listaMessaggi(32), debug);
					return null;
				}
				if (step && pixx >= area11x11)
					MyLog.waitHere(listaMessaggi(22) + pixx, debug);

			} while (pixx < area11x11);
			// MyLog.waitHere();

			// if (imp1.isVisible())
			// imp1.getWindow().toFront();

			imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA,
					sqNEA);
			imp1.updateAndDraw();

			//
			// calcolo SD su imaDiff quando i corrispondenti pixel
			// di imp1 passano il test
			//
			double[] out11 = devStandardNema(imp1, imaDiff, xCenterRoi,
					yCenterRoi, sqNEA, checkPixels);
			if (step)
				MyLog.waitHere(listaMessaggi(23) + out11[0] + "stdDev4= "
						+ out11[1], debug);
			//
			// calcolo SNR finale
			//
			double finalSnr = stat7x7.mean / (out11[1] / Math.sqrt(2));
			if (step)
				MyLog.waitHere(listaMessaggi(24) + finalSnr, debug);

			// // =============================================================
			// userSelection2 = UtilAyv.checkLimits(snr, vetMinimi[2],
			// vetMaximi[2], "SNR");
			// if (userSelection2 == 2) {
			// fast = false;
			// verbose = true;
			// continue;
			// } else if (userSelection2 == 3) {
			// break;
			// }
			// // =============================================================

			String patName = ReadDicom.readDicomParameter(imp1,
					MyConst.DICOM_PATIENT_NAME);

			String codice1 = ReadDicom.readDicomParameter(imp1,
					MyConst.DICOM_SERIES_DESCRIPTION);

			String codice = UtilAyv.getFiveLetters(codice1);

			// String codice = ReadDicom
			// .readDicomParameter(imp1, MyConst.DICOM_SERIES_DESCRIPTION)
			// .substring(0, 4).trim();

			simulataName = fileDir + patName + codice + "sim.zip";

			// passo due volte step (al posto di verbose) per non vedere la
			// simulata in fast
			int[][] classiSimulata = ImageUtils.generaSimulata12classi(
					xCenterRoi, yCenterRoi, sq7, imp1, simulataName, step,
					step, test);

			//
			// calcolo posizione fwhm a metà della MROI
			//

			ImageUtils.imageToFront(iw1);

			//
			// ----------------------------------------------------------
			// Calcolo FWHM
			// la direzione su cui verrà preso il profilo è quella centro
			// ROI - centro cerchio, il segmento su cui tracciamo il profilo
			// -----------------------------------------------------------
			//

			double[] out3 = crossingFrame(xCenterRoi, yCenterRoi,
					xCenterCircle, yCenterCircle, width, height);

			// aveva restituito null
			if (out3 == null)
				MyLog.waitHere("out3==null");

			// ora però devo riordinare i valori restituiti da crossing, in modo
			// che il punto di start del profilo sia quello più vicino al centro
			// ROI.

			double dist1 = MyFwhm.lengthCalculation(out3[0], out3[1],
					xCenterRoi, yCenterRoi);
			double dist2 = MyFwhm.lengthCalculation(out3[2], out3[3],
					xCenterRoi, yCenterRoi);
			int xStartProfile = 0;
			int yStartProfile = 0;
			int xEndProfile = 0;
			int yEndProfile = 0;

			if (dist1 <= dist2) {
				xStartProfile = (int) Math.round(out3[0]);
				yStartProfile = (int) Math.round(out3[1]);
				xEndProfile = (int) Math.round(out3[2]);
				yEndProfile = (int) Math.round(out3[3]);
			} else {
				xStartProfile = (int) Math.round(out3[2]);
				yStartProfile = (int) Math.round(out3[3]);
				xEndProfile = (int) Math.round(out3[0]);
				yEndProfile = (int) Math.round(out3[1]);
			}

			if (!fast) {
				imp1.setRoi(new Line(xStartProfile, yStartProfile, xEndProfile,
						yEndProfile));
				imp1.updateAndDraw();
			}

			IJ.wait(2000);

			// if (imp1.isVisible())
			// imp1.getWindow().toFront();

			double[] profile2 = getProfile(imp1, xStartProfile, yStartProfile,
					xEndProfile, yEndProfile, dimPixel, step);
			// imp1.deleteRoi();
			// step = true;
			double[] outFwhm2 = MyFwhm.analyzeProfile(profile2, dimPixel,
					codice, false, step);

			// =================================================================
			// Effettuo dei controlli "di sicurezza" sui valori calcolati,
			// in modo da evitare possibili sorprese
			// ================================================================

			if (UtilAyv.checkLimits2(stat7x7.mean, minMean7x7, maxMean7x7,
					"SEGNALE ROI 7X7"))
				abort = true;
			if (UtilAyv.checkLimits2(statBkg.mean, minMeanBkg, maxMeanBkg,
					"RUMORE FONDO"))
				abort = true;
			if (UtilAyv.checkLimits2(finalSnr, minSnRatio, maxSnRatio,
					"FINAL SNR RATIO"))
				abort = true;
			if (UtilAyv.checkLimits2(outFwhm2[0], minFWHM, maxFWHM, "FWHM"))
				abort = true;

			//
			// Salvataggio dei risultati nella ResultsTable
			//
			String[][] tabCodici = TableCode.loadTableCSV(MyConst.CODE_FILE);

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
			rt.addValue(2, stat7x7.mean);
			// rt.addValue(3, xCenterRoi);
			// rt.addValue(4, yCenterRoi);
			rt.addValue(3, stat7x7.roiX);
			rt.addValue(4, stat7x7.roiY);
			rt.addValue(5, stat7x7.roiWidth);
			rt.addValue(6, angle);

			rt.incrementCounter();
			rt.addLabel(t1, "Rumore_Fondo");
			rt.addValue(2, (out11[1] / Math.sqrt(2)));
			rt.addValue(3, statBkg.roiX);
			rt.addValue(4, statBkg.roiY);
			rt.addValue(5, statBkg.roiWidth);
			rt.addValue(6, statBkg.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "SnR");
			rt.addValue(2, finalSnr);
			rt.addValue(3, stat7x7.roiX);
			rt.addValue(4, stat7x7.roiY);
			rt.addValue(5, stat7x7.roiWidth);
			rt.addValue(6, stat7x7.roiHeight);

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
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				break;

			case 2:
				// Siemens
				verbose = true;
				ok = selfTestSiemens(verbose);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				break;
			}
		} else {
			UtilAyv.noTest2();
		}
		return;
	}

	public static boolean selfTestGe(boolean verbose) {
		boolean ok = selfTestSiemens(verbose);
		return ok;
	}

	public static boolean selfTestSiemens(boolean verbose) {
		double[] vetReference = referenceSiemens();
		String[] list = { "C001_testP10", "C002_testP10" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		String path1 = path[0];
		String path2 = path[1];

		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean test = false;
		double profond = 30;
		boolean fast = true;
		boolean silent = !verbose;

		ResultsTable rt1 = mainUnifor(path1, path2, autoArgs, profond, "",
				autoCalled, step, verbose, test, fast, silent);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P10_vetName);
		return ok;
	}

	/**
	 * Siemens test image expected results
	 * 
	 * @return
	 */
	public static double[] referenceSiemens() {

		double simul = 0.0;
		double signal = 355.0;
		// double backNoise = 12.225;
		double backNoise = 10.00347735683198;
		double snRatio = 35.48765967441802;
		// double fwhm = 11.43429317989865;
		double fwhm = 23.977086148658152;
		double num1 = 2763.0;
		double num2 = 1532.0;
		double num3 = 7785.0;
		double num4 = 2634.0;
		double num5 = 2574.0;
		double num6 = 2652.0;
		double num7 = 3054.0;
		double num8 = 3284.0;
		double num9 = 3333.0;
		double num10 = 1755.0;
		double num11 = 354.0;
		double num12 = 33816.0;

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm,
				num1, num2, num3, num4, num5, num6, num7, num8, num9, num10,
				num11, num12 };
		return vetReference;

	}

	/**
	 * Ge (Siemens) test image expected results maintained fou uniformity with
	 * earlier plugins
	 * 
	 * @return
	 */
	public static double[] referenceGe() {

		double simul = 0.0;
		double signal = 355.0;
		double backNoise = 10.00347735683198;
		double snRatio = 35.48765967441802;
		double fwhm = 23.977086148658152;
		double num1 = 2763.0;
		double num2 = 1532.0;
		double num3 = 7785.0;
		double num4 = 2634.0;
		double num5 = 2574.0;
		double num6 = 2652.0;
		double num7 = 3054.0;
		double num8 = 3284.0;
		double num9 = 3333.0;
		double num10 = 1755.0;
		double num11 = 354.0;
		double num12 = 33816.0;

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm,
				num1, num2, num3, num4, num5, num6, num7, num8, num9, num10,
				num11, num12 };
		return vetReference;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {
		boolean verbose = false;
		boolean ok = selfTestSiemens(verbose);
		if (ok) {
			IJ.log("Il test di p10rmn_ UNIFORMITA' SUPERFICIALE è stato SUPERATO");
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
	 *            soglia di conteggio, vengono contati i pixel che la superano
	 * @param paintPixels
	 *            switch per test, se attivato vengono colorati i pixels di cui
	 *            viene effettuato il conteggio. Utilizzato per verificare che
	 *            le varie ROI siano posizionate correttamente
	 * @return pixel che superano la soglia
	 */
	public static int countPixTest(ImagePlus imp1, int sqX, int sqY, int sqR,
			double limit, boolean paintPixels) {
		int offset = 0;
		int w = 0;
		int count1 = 0;
		short[] pixels2 = null;

		// ======================================================================
		// per sicurezza forzo lo switch a false, poi lo toglierò dai parametri
		// in modo che non venga mai utilizzato, neanche da me, , tutte le
		// volte che viene attivato crea enormi problemi al funzionamento di
		// lavoro
		paintPixels = false;
		// ======================================================================

		if (imp1 == null) {
			IJ.error("CountPixTest ricevuto null");
			return (0);
		}

		// MyLog.waitHere("sqX= "+sqX+" sqY= "+sqY+" sqR= "+sqR);
		int width = imp1.getWidth();
		short[] pixels1 = UtilAyv.truePixels(imp1);
		ImageProcessor ip1 = imp1.getProcessor();
		if (paintPixels) {
			pixels2 = (short[]) ip1.getPixels();
		}

		for (int y1 = sqY - sqR / 2; y1 <= (sqY + sqR / 2); y1++) {
			offset = y1 * width;
			for (int x1 = sqX - sqR / 2; x1 <= (sqX + sqR / 2); x1++) {
				w = offset + x1;
				if (w >= 0 && w < pixels1.length && pixels1[w] > limit) {
					if (paintPixels) {
						// if (w >= 0 && w < pixels2.length)
						// MyLog.waitHere("sono entrato, painPixels= "+paintPixels);
						pixels2[w] = 4096;
					}
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

	private static double[] getProfile(ImagePlus imp1, int ax, int ay, int bx,
			int by, double dimPixel, boolean step) {

		if (imp1 == null) {
			IJ.error("getProfile  ricevuto null");
			return (null);
		}
		imp1.setRoi(new Line(ax, ay, bx, by));
		Roi roi1 = imp1.getRoi();
		imp1.killRoi();
		double[] profi1 = ((Line) roi1).getPixels(); // profilo non mediato
		profi1[profi1.length - 1] = 0; // azzero a mano l'ultimo pixel
		if (step) {
			imp1.updateAndDraw();
			ButtonMessages.ModelessMsg("Profilo non mediato  <50>", "CONTINUA");
		}
		return (profi1);
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

	// private static void msgSimulata() {
	// ButtonMessages.ModelessMsg("Immagine Simulata", "CONTINUA");
	// }

	/**
	 * Dati i punti di inizio e fine di un segmento, restituisce il valore
	 * dell'angolo theta, effettuando la conversione da coordinate rettangolari
	 * (x,y) a coordinate polari (r, theta). NB: tiene conto che in ImageJ la
	 * coordinata Y ha lo 0 in alto a sx, anzichè in basso a sx, come siamo
	 * soliti a vedere il piano cartesiano
	 * 
	 * @param ax
	 *            coordinata X inizio
	 * @param ay
	 *            coordinata Y inizio
	 * @param bx
	 *            coordinata X fine
	 * @param by
	 *            coordinata Y fine
	 * @return valore dell'angolo in radianti
	 */
	public static double angoloRad(double ax, double ay, double bx, double by) {

		double dx = ax - bx;
		double dy = by - ay; // dy è all'incontrario, per le coordinate di
								// ImageJ
		double theta = Math.atan2(dy, dx);
		return theta;

	}

	/**
	 * Calcola le coordinate del centro ROI sul segmento circonferenza - centro,
	 * alla profondità desiderata
	 * 
	 * @param ax
	 *            coordinata X su circonferenza
	 * @param ay
	 *            coordinata Y su circonferenza
	 * @param bx
	 *            coordinata X del centro
	 * @param by
	 *            coordinata Y del centro
	 * @param prof
	 *            profondità centro ROI
	 * @return vettore coordinate centro ROI
	 */
	public static double[] interpolaProfondCentroROI(double ax, double ay,
			double bx, double by, double prof) {

		double ang1 = angoloRad(ax, ay, bx, by);

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

		// MyLog.waitHere("ax=" + ax + " ay=" + ay + " bx=" + bx + " by=" + by
		// + " prof=" + prof + "cx=" + cx + " cy=" + cy);
		return out;
	}

	/***
	 * Effettua lo smooth su 3 pixels di un profilo
	 * 
	 * @param profile1
	 *            profilo
	 * @param loops
	 *            numerompassaggi
	 * @return profilo dopo smooth
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

	/***
	 * Effettua lo smooth su 3 pixels di un profilo
	 * 
	 * @param profile1
	 *            profilo
	 * @param loops
	 *            numerompassaggi
	 * @return profilo dopo smooth
	 */
	public static double[][] smooth3(double[][] profile1, int loops) {

		double[][] profile2 = new double[profile1.length][profile1[0].length];
		for (int i1 = 0; i1 < profile1.length; i1++) {
			for (int j1 = 1; j1 < profile1[0].length - 1; j1++) {
				profile2[i1][j1] = profile1[i1][j1];
			}
		}

		for (int i1 = 0; i1 < loops; i1++) {
			for (int j1 = 1; j1 < profile1[0].length - 1; j1++)
				profile2[1][j1] = (profile2[1][j1 - 1] + profile2[1][j1] + profile2[1][j1 + 1]) / 3;
		}
		return profile2;

	}

	/**
	 * Calcolo della Edge Response Function (ERF)
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
	}

	/**
	 * Interpolazione lineare di un punto su di un segmento
	 * 
	 * @param x0
	 *            coordinata X inizio
	 * @param y0
	 *            coordinata Y inizio
	 * @param x1
	 *            coordinata X fine
	 * @param y1
	 *            coordinata X fine
	 * @param x2
	 *            valore X di cui calcolare la Y
	 * @return valore Y calcolato
	 */
	public static double linearInterpolation(double x0, double y0, double x1,
			double y1, double x2) {

		double y2 = y0 + ((x2 - x0) * y1 - (x2 - x0) * y0) / (x1 - x0);

		return y2;
	}

	/***
	 * Copied from http://billauer.co.il/peakdet.htm Peak Detection using MATLAB
	 * Author: Eli Billauer Riceve in input un profilo di una linea, costituito
	 * da una matrice con i valori x, y , z di ogni punto. Restituisce le
	 * coordinate x, y, x degli eventuali minimi e maximi
	 * 
	 * @param profile
	 * @param delta
	 * @return
	 */
	public static ArrayList<ArrayList<Double>> peakDet2(double[][] profile,
			double delta) {

		double max = Double.MIN_VALUE;
		double min = Double.MAX_VALUE;
		ArrayList<ArrayList<Double>> matout = new ArrayList<ArrayList<Double>>();

		ArrayList<Double> maxtabx = new ArrayList<Double>();
		ArrayList<Double> maxtaby = new ArrayList<Double>();
		ArrayList<Double> maxtabz = new ArrayList<Double>();
		ArrayList<Double> mintabx = new ArrayList<Double>();
		ArrayList<Double> mintaby = new ArrayList<Double>();
		ArrayList<Double> mintabz = new ArrayList<Double>();

		double[] vetx = new double[profile[0].length];
		double[] vety = new double[profile[0].length];
		double[] vetz = new double[profile[0].length];
		for (int i1 = 0; i1 < profile[0].length; i1++) {
			vetx[i1] = profile[0][i1];
			vety[i1] = profile[1][i1];
			vetz[i1] = profile[2][i1];
		}

		double maxposx = -1.0;
		double minposx = -1.0;
		double maxposy = -1.0;
		double minposy = -1.0;
		boolean lookformax = true;
		double mean1 = 0;
		double sum1 = 0;
		for (int i1 = 0; i1 < vetz.length; i1++) {
			sum1 += vetz[i1];
		}
		mean1 = sum1 / vetz.length;
		// MyLog.waitHere("mean1= " + mean1);

		for (int i1 = 0; i1 < vetz.length; i1++) {
			double valz = vetz[i1];
			if (valz > max) {
				max = valz;
				maxposx = vetx[i1];
				maxposy = vety[i1];
			}
			if (valz < min) {
				min = valz;
				minposx = vetx[i1];
				minposy = vety[i1];
			}
			stateChange(lookformax);
			// -------------------------------
			// aggiungo 0.5 alle posizioni trovate
			// -------------------------------

			maxposx += .5;
			maxposy += .5;

			if (lookformax) {
				if (valz < max - delta) {
					maxtabx.add((Double) maxposx);
					maxtaby.add((Double) maxposy);
					maxtabz.add((Double) max);
					min = valz;
					minposx = vetx[i1];
					minposy = vety[i1];
					lookformax = false;
				}
			} else {
				if (valz > min + delta) {
					// if (valy > min + delta + mean1 * 10) {
					mintabx.add((Double) minposx);
					mintaby.add((Double) minposy);
					mintabz.add((Double) min);
					max = valz;
					maxposx = vetx[i1];
					maxposy = vety[i1];
					lookformax = true;
				}
			}

		}
		// MyLog.logArrayList(mintabx, "############## mintabx #############");
		// MyLog.logArrayList(mintaby, "############## mintaby #############");
		// MyLog.logArrayList(mintabz, "############## mintabz #############");
		// MyLog.logArrayList(maxtabx, "############## maxtabx #############");
		// MyLog.logArrayList(maxtaby, "############## maxtaby #############");
		// MyLog.logArrayList(maxtabz, "############## maxtabz #############");

		matout.add(mintabx);
		matout.add(mintaby);
		matout.add(mintabz);

		matout.add(maxtabx);
		matout.add(maxtaby);
		matout.add(maxtabz);

		return matout;
	}

	/***
	 * Impulso al fronte di salita
	 * 
	 * @param input
	 */
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

	/**
	 * Riceve una ImagePlus con impostata una Line, restituisce le coordinate
	 * dei 2 picchi. Se i picchi non sono 1 oppure 2, restituisce null.
	 * 
	 * @param imp1
	 * @param dimPixel
	 * @param title
	 * @param showProfiles
	 * @return
	 */
	public static double[][] profileAnalyzer(ImagePlus imp1, double dimPixel,
			String title, boolean showProfiles) {

		// MyLog.waitHere("showProfiles= " + showProfiles);
		double[][] profi3 = MyLine.decomposer(imp1);
		// MyLog.logMatrix(profi3, "profi3");

		ArrayList<ArrayList<Double>> matOut = peakDet2(profi3, 100.);
		double[][] peaks1 = new InputOutput()
				.fromArrayListToDoubleTable(matOut);

		if (peaks1 == null) {
			MyLog.waitHere("peaks1 == null");
			return null;
		}
		if (peaks1.length == 0) {
			// MyLog.waitHere("peaks1.length == 0");
			return null;
		}
		if (peaks1[0].length == 0) {
			// MyLog.waitHere("peaks1[0].length == 0");
			return null;
		}
		if (peaks1[0].length > 2) {
			// MyLog.waitHere("peaks1[0].length > 2");
			return null;
		}

		// MyLog.logMatrix(peaks1, "peaks1");
		// MyLog.waitHere();

		double[] xPoints = new double[peaks1[0].length];
		double[] yPoints = new double[peaks1[0].length];
		double[] zPoints = new double[peaks1[0].length];
		for (int i1 = 0; i1 < peaks1[0].length; i1++) {
			xPoints[i1] = peaks1[3][i1];
			yPoints[i1] = peaks1[4][i1];
			zPoints[i1] = peaks1[5][i1];
		}

		// MyLog.logVector(xPoints, "xPoints");
		// MyLog.logVector(yPoints, "yPoints");
		// MyLog.logVector(zPoints, "zPoints");
		// MyLog.waitHere();

		if (showProfiles) {
			Plot plot2 = MyPlot.basePlot2(profi3, title, Color.GREEN);
			plot2.draw();
			plot2.setColor(Color.red);
			plot2.addPoints(xPoints, zPoints, PlotWindow.CIRCLE);
			plot2.show();
			MyLog.waitHere(listaMessaggi(5), debug);

			// new WaitForUserDialog("002 premere  OK").show();
		}

		if (WindowManager.getFrame(title) != null) {
			IJ.selectWindow(title);
			IJ.run("Close");
		}

		return peaks1;
	}

	/***
	 * Questo è il fitCircle preso da ImageJ (ij.plugins.Selection.java, con
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
	 * authors: Nikolai Chernov, Michael Doube, Ved Sharma
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

		// messo imp.setRoi anzichè IJ.makeOval perchè permette di non mostrare
		// l'immagine
		imp.setRoi(new OvalRoi((int) Math.round(CenterX - radius), (int) Math
				.round(CenterY - radius), (int) Math.round(2 * radius),
				(int) Math.round(2 * radius)));
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

		return clips;
	}

	/**
	 * Trasformazione delle coordinate dei punti in equazione esplicita della
	 * retta
	 * 
	 * @param x0
	 *            coordinata X inizio
	 * @param y0
	 *            coordinata Y inizio
	 * @param x1
	 *            coordinata X fine
	 * @param y1
	 *            coordinata Y fine
	 * @return vettore con parametri equazione
	 */
	public static double[] fromPointsToEquLineExplicit(double x0, double y0,
			double x1, double y1) {
		// la formula esplicita è y = mx + b
		// in cui m è detta anche slope (pendenza) e b intercept (intercetta)
		// non può rappresentare rette verticali
		double[] out = new double[2];

		double m = (y1 - y0) / (x1 - x0);

		double b = y0 - m * x0;

		out[0] = m;
		out[1] = b;
		return out;
	}

	/**
	 * Trasformazione delle coordinate dei punti in equazione implicita della
	 * retta
	 * 
	 * @param x0
	 *            coordinata X inizio
	 * @param y0
	 *            coordinata Y inizio
	 * @param x1
	 *            coordinata X fine
	 * @param y1
	 *            coordinata Y fine
	 * @return vettore con parametri equazione
	 */
	public static double[] fromPointsToEquLineImplicit(double x0, double y0,
			double x1, double y1) {
		// la formula implicita è ax + by + c = 0
		double[] out = new double[3];

		double a = y0 - y1;
		double b = x1 - x0;
		double c = x0 * y1 - x1 * y0;

		out[0] = a;
		out[1] = b;
		out[2] = c;

		return out;
	}

	public static double[] fromPointsToEquCirconferenceImplicit(double cx,
			double cy, double radius) {
		// la formula implicita è x^2 + y^2 + ax + by + c = 0
		double[] out = new double[3];

		double a = -2 * cx;
		double b = -2 * cy;
		double c = cx * cx + cy * cy - radius * radius;

		out[0] = a;
		out[1] = b;
		out[2] = c;

		return out;
	}

	/**
	 * Determinazione dei crossing points tra un raggio, di cui si conoscono
	 * solo due punti e la circonferenza. *
	 * 
	 * @param x0
	 *            coord x punto 0
	 * @param y0
	 *            coord y punto 0
	 * @param x1
	 *            coord x punto 1
	 * @param y1
	 *            coord y punto 1
	 * @param xc
	 *            coord x centro
	 * @param yc
	 *            coord y centro
	 * @param rc
	 *            raggio
	 * @return
	 */
	public static double[] getCircleLineCrossingPoints(double x0, double y0,
			double x1, double y1, double xc, double yc, double rc) {

		double[] out = null;
		double bax = x1 - x0;
		double bay = y1 - y0;
		double cax = xc - x0;
		double cay = yc - y0;
		double a = bax * bax + bay * bay;
		double bby2 = bax * cax + bay * cay;
		double c = cax * cax + cay * cay - rc * rc;
		double pby2 = bby2 / a;
		double q = c / a;
		double disc = pby2 * pby2 - q;
		if (disc < 0)
			return null;

		double tmpSqrt = Math.sqrt(disc);
		double abScaling1 = -pby2 + tmpSqrt;
		double abScaling2 = -pby2 - tmpSqrt;
		double o1x = x0 - bax * abScaling1;
		double o1y = y0 - bay * abScaling1;
		if (disc == 0) {
			out = new double[2];
			out[0] = o1x;
			out[1] = o1y;
		}
		double o2x = x0 - bax * abScaling2;
		double o2y = y0 - bay * abScaling2;
		out = new double[4];
		out[0] = o1x;
		out[1] = o1y;
		out[2] = o2x;
		out[3] = o2y;
		return out;
	}

	/**
	 * Determinazione dei crossing points tra la retta della prosecuzione di un
	 * segmento ed i lati del frame. ATTENZIONE: si limita a trovare i punti di
	 * crossing, non li mette in ordine
	 * 
	 * @param x0
	 *            coordinata X inizio
	 * @param y0
	 *            coordinata Y inizio
	 * @param x1
	 *            coordinata X fine
	 * @param y1
	 *            coordinata Y fine
	 * @param width
	 *            larghezza immagine
	 * @param height
	 *            altezza immagine
	 * @return vettore con coordinate clipping points
	 */
	public static double[] crossingFrame(double x0, double y0, double x1,
			double y1, double width, double height) {

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
		boolean upperLeftVertex = false;
		boolean upperRightVertex = false;
		boolean lowerLeftVertex = false;
		boolean lowerRightVertex = false;

		// MyLog.waitHere("a= " + a + " b= " + b + " c= " + c + " width= " +
		// width
		// + " height= " + height);

		double[] clippingPoints = new double[4];
		int count = 0;

		// ora andrò a calcolare il crossing per i vari lati dell'immagine. Mi
		// aspetto di avere due soli crossing. Esiste però un eccezione è il
		// caso particolare in cui il crossing avviene esattamente su di un
		// angolo dell'immagine: in tal caso avrò che is between mi darà il
		// crossing sia per il lato orizzontale che per il lato verticale, per
		// cui mi troverò con 3 crossing. Nel caso ancora più particolare di una
		// diagonale del quadrato mi troverò con quattro cfrossing, anzichè due.
		// ed io devo passare ad imageJ le coordinate di solo due punti.

		// lato superiore
		y = 0;
		x = -(b * y + c) / a;

		// IJ.log("lato superiore x= " + x + " y= " + y);

		upperLeftVertex = UtilAyv.myTestEquals(x, 0D, tolerance);
		upperRightVertex = UtilAyv.myTestEquals(x, width, tolerance);
		if (isBetween(x, 0, width, tolerance)) {
			if (count <= 2) {
				clippingPoints[count++] = x;
				clippingPoints[count++] = y;
			} else {
				MyLog.waitHere("001 ERROR count= " + count);
				return null;
			}
		}

		// lato inferiore
		y = height;
		x = -(b * y + c) / a;
		// IJ.log("lato inferiore x= " + x + " y= " + y);
		lowerLeftVertex = UtilAyv.myTestEquals(x, 0D, tolerance);
		lowerRightVertex = UtilAyv.myTestEquals(x, width, tolerance);

		if (isBetween(x, 0, width, tolerance)) {
			if (count <= 2) {
				clippingPoints[count++] = x;
				clippingPoints[count++] = y;
			} else {
				MyLog.waitHere("002 ERROR count= " + count);
				return null;
			}
		}

		// lato sinistro
		x = 0;
		y = -(a * x + c) / b;
		// IJ.log("lato sinistro x= " + x + " y= " + y);
		if (isBetween(y, 0, height, tolerance) && (!upperLeftVertex)
				&& (!lowerLeftVertex)) {
			// if (isBetween(y, 0, height, tolerance)) {
			if (count <= 2) {
				clippingPoints[count++] = x;
				clippingPoints[count++] = y;
			} else {
				MyLog.waitHere("003 ERROR count= " + count);
				return null;
			}
		}

		// lato destro
		x = width;
		y = -(a * x + c) / b;
		// IJ.log("lato destro x= " + x + " y= " + y);
		if (isBetween(y, 0, height, tolerance) && (!upperRightVertex)
				&& (!lowerRightVertex)) {
			// if (isBetween(y, 0, height, tolerance)) {
			if (count <= 2) {
				clippingPoints[count++] = x;
				clippingPoints[count++] = y;
			} else {
				MyLog.waitHere("004 ERROR count= " + count);
				return null;
			}
		}
		return clippingPoints;
	}

	/**
	 * Verifica se un valore è all'interno dei limiti assegnati, con una certa
	 * tolleranza
	 * 
	 * @param x1
	 *            valore calcolato
	 * @param low
	 *            limite inferiore
	 * @param high
	 *            limite superiore
	 * @param tolerance
	 *            tolleranza
	 * @return true se il valore è valido (entro i limiti)
	 */
	public static boolean isBetween(double x1, double low, double high,
			double tolerance) {

		if (low < high) {
			return ((x1 >= (low - tolerance)) && (x1 <= (high + tolerance)));
		} else {
			return ((x1 >= (high - tolerance)) && (x1 <= (low + tolerance)));
		}
	}

	/**
	 * Test images extraction on a temporary directory test2.jar
	 * 
	 * @return path of temporarary directory
	 */
	public String findTestImages() {

		InputOutput io = new InputOutput();
		long num1 = io.extractFromJAR2(MyConst.TEST_FILE, "C001_testP10",
				"./Test2/");
		long num2 = io.extractFromJAR2(MyConst.TEST_FILE, "C002_testP10",
				"./Test2/");
		if (num1 <= 0 || num2 <= 0)
			MyLog.waitHere("errore estrazione files da test2.jar");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY)
				.getPath();
		return (home1);
	}

	/**
	 * Ricerca della posizione della ROI per il calcolo dell'uniformità
	 * 
	 * @param imp11
	 *            immagine di input
	 * @param profond
	 *            profondità ROI
	 * @param info1
	 *            messaggio esplicativo
	 * @param autoCalled
	 *            flag true se chiamato in automatico
	 * @param step
	 *            flag true se funzionamento passo - passo
	 * @param verbose
	 *            flag true se funzionamento verbose
	 * @param test
	 *            flag true se in test
	 * @param fast
	 *            flag true se modo batch
	 * @return vettore con dati ROI
	 */
	public static double[] positionSearch(ImagePlus imp11, double profond,
			String info1, boolean autoCalled, boolean step, boolean verbose,
			boolean test, boolean fast) {
		//
		// ================================================================================
		// Inizio calcoli geometrici
		// ================================================================================
		//

		boolean debug = true;
		boolean manual = false;
		boolean demo = !fast;
		boolean showProfiles = demo;
		// MyLog.waitHere("showProfiles= " + showProfiles);

		double ax = 0;
		double ay = 0;
		int xCenterCircle = 0;
		int yCenterCircle = 0;
		int diamCircle = 0;

		double xMaxima = 0;
		double yMaxima = 0;
		double angle11 = 0;
		double xCenterRoi = 0;
		double yCenterRoi = 0;
		double maxFitError = 30;
		Overlay over12 = new Overlay();

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(
						ReadDicom.readDicomParameter(imp11,
								MyConst.DICOM_PIXEL_SPACING), 1));

		ImageWindow iw11 = null;
		if (demo)
			iw11 = imp11.getWindow();

		int width = imp11.getWidth();
		int height = imp11.getHeight();
		ImagePlus imp12 = imp11.duplicate();
		imp12.setTitle("DUP");

		//
		// -------------------------------------------------
		// Determinazione del cerchio
		// -------------------------------------------------
		//
		// IJ.run(imp12, "Smooth", "");

		ImageProcessor ip12 = imp12.getProcessor();
		if (demo) {
			UtilAyv.showImageMaximized(imp12);
			ImageUtils.imageToFront(imp12);
		}

		ip12.setSnapshotCopyMode(true);
		ip12.smooth();
		ip12.setSnapshotCopyMode(false);
		imp12.updateAndDraw();
		if (step)
			MyLog.waitHere(listaMessaggi(2), debug);

		// new WaitForUserDialog("Eseguito SMOOTH").show();

		ip12.findEdges();
		imp12.updateAndDraw();

		if (step)
			MyLog.waitHere(listaMessaggi(3), debug);
		// new WaitForUserDialog("Eseguito FIND EDGES").show();

		// ImagePlus imp12 = imp122;
		tidalWave(imp12, 200);
		imp12.updateAndDraw();
		if (step)
			MyLog.waitHere(listaMessaggi(4), debug);

		// UtilAyv.showImageMaximized(imp12);
		// ImageWindow iw12 = WindowManager.getCurrentWindow();
		// MyLog.waitHere("iw12= "+iw122);

		imp12.setOverlay(over12);

		double[][] peaks1 = new double[4][1];
		double[][] peaks2 = new double[4][1];
		double[][] peaks3 = new double[4][1];
		double[][] peaks4 = new double[4][1];
		double[][] peaks5 = new double[4][1];
		double[][] peaks6 = new double[4][1];
		double[][] peaks7 = new double[4][1];
		double[][] peaks8 = new double[4][1];
		int[] xcoord = new int[2];
		int[] ycoord = new int[2];
		boolean manualOverride = false;

		// --------DIAGONALE SINISTRA---------------------
		xcoord[0] = 0;
		ycoord[0] = 0;
		xcoord[1] = width;
		ycoord[1] = height;
		imp12.setRoi(new Line(xcoord[0], ycoord[0], xcoord[1], ycoord[1]));
		if (demo) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}

		// if (strokewidth)
		// imp12.getRoi().setStrokeWidth(strWidth);
		peaks5 = profileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE SINISTRA 2", showProfiles);

		if (peaks5 != null)
			plotPoints(imp12, over12, peaks5);

		// --------DIAGONALE DESTRA---------------------
		xcoord[0] = width;
		ycoord[0] = 0;
		xcoord[1] = 0;
		ycoord[1] = height;
		imp12.setRoi(new Line(xcoord[0], ycoord[0], xcoord[1], ycoord[1]));
		if (demo) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}
		// if (strokewidth)
		// imp12.getRoi().setStrokeWidth(strWidth);
		peaks6 = profileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE DESTRA 2", false);
		if (peaks6 != null)
			plotPoints(imp12, over12, peaks6);

		// -------- ORIZZONTALE ---------------------
		xcoord[0] = 0;
		ycoord[0] = height / 2;
		xcoord[1] = width;
		ycoord[1] = height / 2;

		imp12.setRoi(new Line(xcoord[0], ycoord[0], xcoord[1], ycoord[1]));
		if (demo) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}
		// if (strokewidth)
		// imp12.getRoi().setStrokeWidth(strWidth);

		peaks1 = profileAnalyzer(imp12, dimPixel, "BISETTRICE ORIZZONTALE 2",
				false);
		// PLOTTAGGIO PUNTI
		if (peaks1 != null)
			plotPoints(imp12, over12, peaks1);

		// NOTA BENE: sulla bisettrice (e ricordiamoci, è la bisettrice
		// dell'immagine) potrebbe esserci la bolla d'aria a sinistra, quindi
		// non dovrei utilizzare questo punto per la determinazione automatica
		// del centro, poichè può introdurre un leggero shift del centro

		// -------- VERTICALE ---------------------
		xcoord[0] = width / 2;
		ycoord[0] = 0;
		xcoord[1] = width / 2;
		ycoord[1] = height;
		imp12.setRoi(new Line(xcoord[0], ycoord[0], xcoord[1], ycoord[1]));
		if (demo) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}

		// if (strokewidth)
		// imp12.getRoi().setStrokeWidth(strWidth);
		peaks2 = profileAnalyzer(imp12, dimPixel, "BISETTRICE VERTICALE 2",
				false);

		// PLOTTAGGIO PUNTI
		if (peaks2 != null)
			plotPoints(imp12, over12, peaks2);
		// MyLog.logMatrix(peaks2, "peaks2");
		// MyLog.waitHere();

		// -------- INCERTA SX---------------------
		xcoord[0] = width / 4;
		ycoord[0] = 0;
		xcoord[1] = width * 3 / 4;
		ycoord[1] = height;
		imp12.setRoi(new Line(xcoord[0], ycoord[0], xcoord[1], ycoord[1]));
		if (demo) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}
		// if (strokewidth)
		// imp12.getRoi().setStrokeWidth(strWidth);
		peaks3 = profileAnalyzer(imp12, dimPixel, "BISETTRICE INCERTA SX 2",
				false);
		// PLOTTAGGIO PUNTI
		if (peaks3 != null)
			plotPoints(imp12, over12, peaks3);
		// MyLog.logMatrix(peaks3, "peaks3");
		// MyLog.waitHere();

		// -------- INCERTA DX---------------------
		xcoord[0] = width * 3 / 4;
		ycoord[0] = 0;
		xcoord[1] = width / 4;
		ycoord[1] = height;
		imp12.setRoi(new Line(xcoord[0], ycoord[0], xcoord[1], ycoord[1]));
		if (demo) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}
		// if (strokewidth)
		// imp12.getRoi().setStrokeWidth(strWidth);
		peaks4 = profileAnalyzer(imp12, dimPixel, "BISETTRICE INCERTA DX 2",
				false);
		// PLOTTAGGIO PUNTI
		if (peaks4 != null)
			plotPoints(imp12, over12, peaks4);
		// MyLog.logMatrix(peaks4, "peaks4");
		// MyLog.waitHere();

		// --------DIAGONALE DESTRA extra---------------------
		xcoord[0] = width;
		ycoord[0] = height * 1 / 4;
		xcoord[1] = 0;
		ycoord[1] = height * 3 / 4;
		imp12.setRoi(new Line(xcoord[0], ycoord[0], xcoord[1], ycoord[1]));
		if (demo) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}
		// if (strokewidth)
		// imp12.getRoi().setStrokeWidth(strWidth);
		peaks7 = profileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE SINISTRA EXTRA 2", false);
		// PLOTTAGGIO PUNTI
		if (peaks7 != null)
			plotPoints(imp12, over12, peaks7);
		// --------DIAGONALE DESTRA extra---------------------
		xcoord[0] = 0;
		ycoord[0] = height * 1 / 4;
		xcoord[1] = width;
		ycoord[1] = height * 3 / 4;
		imp12.setRoi(new Line(xcoord[0], ycoord[0], xcoord[1], ycoord[1]));
		if (demo) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}
		// if (strokewidth)
		// imp12.getRoi().setStrokeWidth(strWidth);
		peaks8 = profileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE DESTRA EXTRA 2", false);
		// PLOTTAGGIO PUNTI
		if (peaks8 != null)
			plotPoints(imp12, over12, peaks8);
		// MyLog.logMatrix(peaks8, "peaks8");
		// MyLog.waitHere();
		// --------------------------------------------
		if (demo) {
			MyLog.waitHere(listaMessaggi(12), debug);
		}
		// --------------------------------------------

		int npeaks1 = 0;
		int npeaks2 = 0;
		int npeaks3 = 0;
		int npeaks4 = 0;
		int npeaks5 = 0;
		int npeaks6 = 0;
		int npeaks7 = 0;
		int npeaks8 = 0;

		if (peaks1 != null)
			npeaks1 = peaks1[2].length - 1;
		if (peaks2 != null)
			npeaks2 = peaks2[2].length - 1;
		if (peaks3 != null)
			npeaks3 = peaks3[2].length;
		if (peaks4 != null)
			npeaks4 = peaks4[2].length;
		if (peaks5 != null)
			npeaks5 = peaks5[2].length;
		if (peaks6 != null)
			npeaks6 = peaks6[2].length;
		if (peaks7 != null)
			npeaks7 = peaks7[2].length;
		if (peaks8 != null)
			npeaks8 = peaks8[2].length;
		int len3 = npeaks1 + npeaks2 + npeaks3 + npeaks4 + npeaks5 + npeaks6
				+ npeaks7 + npeaks8;

		int[] xPoints3 = new int[len3];
		int[] yPoints3 = new int[len3];
		int j1 = -1;

		if (demo)
			MyLog.waitHere(listaMessaggi(14), debug);
		// della bisettice orizzontale prendo solo il picco di dx
		if (npeaks1 == 1) {
			j1++;
			xPoints3[j1] = (int) (peaks1[3][1]);
			yPoints3[j1] = (int) (peaks1[4][1]);
		}

		// della bisettice verticale prendo solo il picco in basso
		if (npeaks2 == 1) {
			j1++;
			xPoints3[j1] = (int) (peaks2[3][1]);
			yPoints3[j1] = (int) (peaks2[4][1]);
		}
		for (int i1 = 0; i1 < npeaks3; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks3[3][i1]);
			yPoints3[j1] = (int) (peaks3[4][i1]);
		}
		for (int i1 = 0; i1 < npeaks4; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks4[3][i1]);
			yPoints3[j1] = (int) (peaks4[4][i1]);
		}
		for (int i1 = 0; i1 < npeaks5; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks5[3][i1]);
			yPoints3[j1] = (int) (peaks5[4][i1]);
		}
		for (int i1 = 0; i1 < npeaks6; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks6[3][i1]);
			yPoints3[j1] = (int) (peaks6[4][i1]);
		}
		for (int i1 = 0; i1 < npeaks7; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks7[3][i1]);
			yPoints3[j1] = (int) (peaks7[4][i1]);
		}
		for (int i1 = 0; i1 < npeaks8; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks8[3][i1]);
			yPoints3[j1] = (int) (peaks8[4][i1]);
		}

		// MyLog.logVector(xPoints3, "xPoints3");
		// MyLog.logVector(yPoints3, "yPoints3");
		// MyLog.waitHere();
		over12.clear();

		// ----------------------------------------------------------------------
		// Verifica di avere trovato almeno 3 punti, altrimenti chiede la
		// selezione manuale del cerchio
		// -------------------------------------------------------------------

		if (xPoints3.length < 3 || test) {
			UtilAyv.showImageMaximized(imp11);
			MyLog.waitHere(listaMessaggi(17), debug);
			manual = true;
		}

		if (!manual) {
			imp12.setRoi(new PointRoi(xPoints3, yPoints3, xPoints3.length));
			if (demo) {
				imp12.updateAndDraw();
				over12.addElement(imp12.getRoi());
				over12.setStrokeColor(Color.green);
				imp12.setOverlay(over12);
				imp12.updateAndDraw();
				MyLog.waitHere(listaMessaggi(15), debug);
			}
			// ---------------------------------------------------
			// eseguo ora fitCircle per trovare centro e dimensione del
			// fantoccio
			// ---------------------------------------------------
			fitCircle(imp12);
			if (demo) {
				imp12.getRoi().setStrokeColor(Color.red);
				over12.addElement(imp12.getRoi());
			}

			if (demo)
				MyLog.waitHere(listaMessaggi(16), debug);
			Rectangle boundRec = imp12.getProcessor().getRoi();
			xCenterCircle = Math.round(boundRec.x + boundRec.width / 2);
			yCenterCircle = Math.round(boundRec.y + boundRec.height / 2);
			diamCircle = boundRec.width;
			if (!manualOverride)
				writeStoredRoiData(boundRec);

			MyCircleDetector.drawCenter(imp12, over12, xCenterCircle,
					yCenterCircle, Color.red);

			if (demo)
				MyLog.waitHere(listaMessaggi(17), debug);

			// ----------------------------------------------------------
			// Misuro l'errore sul fit rispetto ai punti imposti
			// -----------------------------------------------------------
			double[] vetDist = new double[xPoints3.length];
			double sumError = 0;
			for (int i1 = 0; i1 < xPoints3.length; i1++) {
				vetDist[i1] = pointCirconferenceDistance(xPoints3[i1],
						yPoints3[i1], xCenterCircle, yCenterCircle,
						diamCircle / 2);
				sumError += Math.abs(vetDist[i1]);
			}
			if (sumError > maxFitError) {
				// -------------------------------------------------------------
				// disegno il cerchio ed i punti, in modo da date un feedback
				// grafico al messaggio di eccessivo errore nel fit
				// -------------------------------------------------------------
				UtilAyv.showImageMaximized(imp11);
				over12.clear();
				imp11.setOverlay(over12);
				imp11.setRoi(new OvalRoi(xCenterCircle - diamCircle / 2,
						yCenterCircle - diamCircle / 2, diamCircle, diamCircle));
				imp11.getRoi().setStrokeColor(Color.red);
				over12.addElement(imp11.getRoi());
				imp11.setRoi(new PointRoi(xPoints3, yPoints3, xPoints3.length));
				imp11.getRoi().setStrokeColor(Color.green);
				over12.addElement(imp11.getRoi());
				imp11.deleteRoi();
				MyLog.waitHere(listaMessaggi(18), debug);
				manual = true;
			}

		}

		// ----------------------------------------------------------------------
		// Verifica di avere trovato almeno 3 punti, altrimenti chiede la
		// selezione manuale del cerchio
		// -------------------------------------------------------------------
		if (xPoints3.length >= 3) {
			imp12.setRoi(new PointRoi(xPoints3, yPoints3, xPoints3.length));
			fitCircle(imp12);
			if (step) {
				over12.addElement(imp12.getRoi());
				over12.setStrokeColor(Color.red);
			}

		} else {
			fast = false;
			UtilAyv.showImageMaximized(imp12);
			imp12.setRoi(new OvalRoi((width / 2) - 100, (height / 2) - 100,
					200, 200));
			MyLog.waitHere(listaMessaggi(19), debug);

			//
			// Ho così risolto la mancata localizzazione automatica del
			// fantoccio
			//
		}

		// ==========================================================================
		// ==========================================================================
		// porto in primo piano l'immagine originale
		ImageUtils.imageToFront(iw11);
		// ==========================================================================
		// ==========================================================================
		imp11.setOverlay(over12);

		Rectangle boundRec = imp12.getProcessor().getRoi();

		// x1 ed y1 sono le due coordinate del centro

		xCenterCircle = boundRec.x + boundRec.width / 2;
		yCenterCircle = boundRec.y + boundRec.height / 2;
		// over12.setStrokeColor(Color.red);
		// imp12.setOverlay(over12);

		//

		// ----------------------------------------------------------
		// disegno la ROI del maxima, a solo scopo dimostrativo !
		// ----------------------------------------------------------
		//

		// x1 ed y1 sono le due coordinate del punto di maxima

		// double[] out10 = UtilAyv.findMaximumPosition(imp12);

		double[] out10 = MyFilter.maxPosition11x11(imp11);
		xMaxima = out10[0];
		yMaxima = out10[1];
		if (verbose) {
			MyCircleDetector.drawCenter(imp11, over12, (int) xMaxima,
					(int) yMaxima, Color.green);

			if (demo)
				MyLog.waitHere(listaMessaggi(20), debug);

		}
		imp12.killRoi();

		// ===============================================================
		// intersezioni retta - circonferenza
		// ===============================================================

		double[] out11 = getCircleLineCrossingPoints(xCenterCircle,
				yCenterCircle, xMaxima, yMaxima, xCenterCircle, yCenterCircle,
				diamCircle / 2);
		// il punto che ci interesasa sarà quello con minor distanza dal maxima
		double dx1 = xMaxima - out11[0];
		double dx2 = xMaxima - out11[2];
		double dy1 = yMaxima - out11[1];
		double dy2 = yMaxima - out11[3];
		double lun1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
		double lun2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);

		double xBordo = 0;
		double yBordo = 0;
		if (lun1 < lun2) {
			xBordo = out11[0];
			yBordo = out11[1];
		} else {
			xBordo = out11[2];
			yBordo = out11[3];
		}

		if (verbose)
			MyCircleDetector.drawCenter(imp11, over12, (int) xBordo,
					(int) yBordo, Color.pink);

		//
		// -----------------------------------------------------------
		// Calcolo delle effettive coordinate del segmento
		// centro-circonferenza
		// ----------------------------------------------------------
		//
		double xStartRefLine = (double) xCenterCircle;
		double yStartRefLine = (double) yCenterCircle;
		double xEndRefLine = xBordo;
		double yEndRefLine = yBordo;

		imp11.setRoi(new Line(xCenterCircle, yCenterCircle, (int) xBordo,
				(int) yBordo));
		angle11 = imp11.getRoi().getAngle(xCenterCircle, yCenterCircle,
				(int) xBordo, (int) yBordo);

		over12.addElement(imp11.getRoi());
		over12.setStrokeColor(Color.red);
		//
		// -----------------------------------------------------------
		// Calcolo coordinate centro della MROI
		// ----------------------------------------------------------
		//

		double[] out1 = interpolaProfondCentroROI(xEndRefLine, yEndRefLine,
				xStartRefLine, yStartRefLine, profond / dimPixel);
		ax = out1[0];
		ay = out1[1];

		if (verbose) {
			MyCircleDetector.drawCenter(imp11, over12, (int) ax, (int) ay,
					Color.yellow);
			if (demo)
				MyLog.waitHere(listaMessaggi(21), debug);

		}

		int sqNEA = MyConst.P10_NEA_11X11_PIXEL;

		imp11.setRoi((int) ax - sqNEA / 2, (int) ay - sqNEA / 2, sqNEA, sqNEA);
		imp11.updateAndDraw();
		over12.addElement(imp11.getRoi());
		over12.setStrokeColor(Color.red);

		//
		// Se non necessito di un intervento manuale, mi limito a leggere le
		// coordinate della ROI determinata in automatico.
		//

		Rectangle boundRec4 = imp11.getProcessor().getRoi();
		xCenterRoi = boundRec4.getCenterX();
		yCenterRoi = boundRec4.getCenterY();
		imp12.hide();

		// MyLog.waitHere("ax= " + ax + " ay= " + ay + " xCenterRoi= "
		// + xCenterRoi + " yCenterRoi= " + yCenterRoi);
		// }

		if (!fast && !test) {

			ImageUtils.imageToFront(iw11);

			// UtilAyv.showImageMaximized(imp11);
			imp11.setOverlay(over12);
			imp11.setRoi((int) ax - sqNEA / 2, (int) ay - sqNEA / 2, sqNEA,
					sqNEA);
			imp11.updateAndDraw();
			MyLog.waitHere(info1 + "\n \nMODIFICA MANUALE POSIZIONE ROI", debug);
			//
			// Vado a rileggere solo le coordinate della ROI, quelle del
			// cerchio,
			// del punto di maxima e dell'angolo resteranno quelle determinate
			// in
			// precedenza (anche perchè non vengono comunque più utilizzate per
			// i
			// calcoli)
			//
			Rectangle boundRec3 = imp11.getProcessor().getRoi();
			xCenterRoi = boundRec3.getCenterX();
			yCenterRoi = boundRec3.getCenterY();

		}

		double[] out2 = new double[9];
		out2[0] = xCenterRoi;
		out2[1] = yCenterRoi;
		out2[2] = xCenterCircle;
		out2[3] = yCenterCircle;
		out2[4] = xMaxima;
		out2[5] = yMaxima;
		out2[6] = angle11;
		out2[7] = xBordo;
		out2[8] = yBordo;

		return out2;
	}

	/**
	 * Lettura di un valore double da una stringa
	 * 
	 * @param s1
	 *            stringa di input
	 * @return double di output
	 */
	public static double readDouble(String s1) {
		double x = 0;
		try {
			x = (new Double(s1)).doubleValue();
		} catch (Exception e) {
			// MyLog.waitHere("input non numerico= " + s1);
			// MyLog.caller("chiamante=");
			x = Double.NaN;
		}
		return x;
	}

	public static String[] decoderLimiti(String[][] tableLimiti, String vetName) {
		String[] result;
		if (tableLimiti == null)
			MyLog.waitHere("tableLimiti == null");
		if (vetName == null || vetName == "")
			MyLog.waitHere("vetName == none");
		for (int i1 = 0; i1 < tableLimiti.length; i1++) {
			if (tableLimiti[i1][0].equals(vetName)) {
				result = tableLimiti[i1];
				return result;
			}
		}
		return null;
	}

	public static double[] doubleLimiti(String[] in1) {
		double[] result = new double[in1.length - 1];
		int i2 = 0;
		// MyLog.logVector(in1, "in1");
		// MyLog.waitHere();
		for (int i1 = 1; i1 < in1.length; i1++) {

			result[i2++] = readDouble(in1[i1]);
		}
		return result;
	}

	/**
	 * Per p3rmn le due immagini devono essere identiche, a parte che vengono
	 * prese una di seguito all'altra. Testiamo seriesDescription e coil
	 * 
	 * @param imp1
	 * @param imp2
	 * @return
	 */
	public static boolean checkImages(ImagePlus imp1, ImagePlus imp2) {
		boolean ok1 = false;
		boolean ok2 = false;

		String seriesDescription1 = ReadDicom.readDicomParameter(imp1,
				MyConst.DICOM_SERIES_DESCRIPTION);
		String seriesDescription2 = ReadDicom.readDicomParameter(imp2,
				MyConst.DICOM_SERIES_DESCRIPTION);
		if (seriesDescription1.equals(seriesDescription2))
			ok1 = true;
		String coil1 = ReadDicom.getAllCoils(imp1);
		String coil2 = ReadDicom.getAllCoils(imp2);
		if (coil1.equals(coil2))
			ok2 = true;
		return ok1 && ok2;
	}

	/**
	 * Qui sono raggruppati tutti i messaggi del plugin, in questo modo è
	 * facilitata la eventuale modifica / traduzione dei messaggi.
	 * 
	 * @param select
	 * @return
	 */
	public static String listaMessaggi(int select) {
		String[] lista = new String[100];
		// ---------+-----------------------------------------------------------+
		lista[0] = "messaggio 0";
		lista[1] = "messaggio 1";
		lista[2] = "Eseguito SMOOTH";
		lista[3] = "Eseguito EDGE DETECTOR";
		lista[4] = "Eseguito SUBTRACT";
		lista[5] = "Analizzando il profilo del segnale lungo la linea si ricavano \n"
				+ "le coordinate delle due intersezioni con la circonferenza.";
		// ---------+-----------------------------------------------------------+

		lista[12] = "Si tracciano ulteriori linee, passanti per il centro dell'immagine, \n"
				+ "su queste linee si cercano le intersezioni con il cerchio";
		lista[13] = "Analizzando il profilo del segnale lungo la linea si ricavano \n"
				+ "le coordinate delle due intersezioni con la circonferenza.";
		lista[14] = "Per tenere conto delle possibili bolle d'aria del fantoccio, si \n"
				+ "escludono dalla bisettice orizzontale il picco di sinistra e dalla \n"
				+ "bisettrice verticale il picco superiore ";
		lista[15] = "Sono mostrati in verde i punti utilizzati per il fit della circonferenza";
		lista[16] = "La circonferenza risultante dal fit è mostrata in rosso";
		lista[17] = "Il centro del fantoccio è contrassegnato dal pallino rosso";
		lista[18] = "Troppa distanza tra i punti forniti ed il fit del cerchio";
		lista[19] = "Non si riescono a determinare le coordinate di almeno 3 punti del cerchio \n"
				+ "posizionare manualmente una ROI circolare di diametro uguale al fantoccio e\n"
				+ "premere  OK";
		lista[20] = "Analizzando l'intera immagine con una ROI 11x11, viene determinata la \n"
				+ "posizione con la massima media nell'immagine, la posizione è contrassegnata \n"
				+ "dal pallino verde";
		// ---------+-----------------------------------------------------------+
		lista[21] = "Lungo il segmento che unisce il pallino rosso del centro con il pallino verde \n"
				+ "del maximo ed il pallino rosa sulla circonferenza, viene calcolata la posizione \n"
				+ "del centro MROI, contrassegnato dal pallino giallo,  posto alla profondità \n"
				+ "impostata nel file codiciNew.csv per il codice di questo controllo";
		lista[22] = "Accrescimento NEA riuscito, pixels validi= ";
		lista[23] = "mean4= ";
		lista[24] = "SNR finale= ";
		lista[25] = "displayNEA";
		lista[26] = "Segnale medio fondo= ";
		lista[27] = "messaggio 27";
		lista[28] = "messaggio 28";
		lista[29] = "messaggio 29";
		// ---------+-----------------------------------------------------------+
		lista[30] = "MROI 11x11 evidenziata in verde";
		lista[31] = "MROI 7x7 evidenziata in rosso";
		lista[32] = "ATTENZIONE la NEA esce dall'immagine senza riuscire a \n"
				+ "trovare 121 pixel che superino il  test il programma \n"
				+ "TERMINA PREMATURAMENTE";
		lista[33] = "Disegnata Mroi su imaDiff";
		lista[34] = "Preliminary Noise Estimate su MROI";
		lista[35] = "Accrescimento MROI lato= ";
		lista[36] = "messaggio 36";
		lista[37] = "messaggio 37";
		lista[38] = "messaggio 38";
		lista[39] = "messaggio 39";

		// ---------+-----------------------------------------------------------+
		lista[65] = "vetMinimi==null, verificare esistenza della riga P10MIN nel file limiti.csv";
		lista[66] = "vetMaximi==null, verificare esistenza della riga P10MAX nel file limiti.csv";
		// ---------+-----------------------------------------------------------+

		String out = lista[select];
		return out;
	}

	public static void tidalWave(ImagePlus imp1, int level) {
		// ImagePlus imp2 = imp1.duplicate();
		ImageProcessor ip1 = imp1.getProcessor();
		short[] pixels = (short[]) ip1.getPixels();
		for (int i1 = 0; i1 < pixels.length; i1++) {
			pixels[i1] -= level;
			if (pixels[i1] < 0)
				pixels[i1] = 0;
		}
		ip1.resetMinAndMax();
		return;
	}

	/***
	 * Riceve una ImagePlus derivante da un CannyEdgeDetector con impostata una
	 * Line, restituisce le coordinate dei 2 picchi
	 * 
	 * @param imp1
	 * @param dimPixel
	 * @return
	 */
	public static double[][] cannyProfileAnalyzer(ImagePlus imp1,
			double dimPixel, String title, boolean showProfiles, boolean demo,
			boolean debug) {

		double[][] line1 = MyLine.decomposer(imp1);
		int count1 = 0;
		boolean ready1 = false;
		double max1 = 0;
		for (int i1 = 0; i1 < line1[0].length; i1++) {

			if (line1[2][i1] > max1) {
				max1 = line1[2][i1];
				ready1 = true;
			}
			if ((line1[2][i1] == 0) && ready1) {
				max1 = 0;
				count1++;
				ready1 = false;
			}
		}
		// devo ora contare i pixel a 255 che ho trovato, ne accetterò solo 2,
		if (count1 != 2) {
			if (demo)
				MyLog.waitHere("" + title
						+ " trovati un numero di punti diverso da 2, count= "
						+ count1 + " scartiamo questi risultati");
			return null;
		}

		// peaks1 viene utilizzato in un altra routine, per cui gli elementi 0
		// ed 1 sono utilizzati per altro, li lascio a 0
		double[][] peaks1 = new double[4][count1];

		int count2 = 0;
		boolean ready2 = false;
		double max2 = 0;
		for (int i1 = 0; i1 < line1[0].length; i1++) {

			if (line1[2][i1] > max2) {
				peaks1[2][count2] = line1[0][i1];
				peaks1[3][count2] = line1[1][i1];
				max2 = line1[2][i1];
				ready2 = true;
			}
			if ((line1[2][i1] == 0) && ready2) {
				max2 = 0;
				count2++;
				ready2 = false;
			}
		}

		// ----------------------------------------
		// AGGIUNGO 0.5 AI PUNTI TROVATI
		// ---------------------------------------

		for (int i1 = 0; i1 < peaks1.length; i1++) {
			for (int i2 = 0; i2 < peaks1[0].length; i2++)
				if (peaks1[i1][i2] > 0)
					peaks1[i1][i2] = peaks1[i1][i2] + 1;
		}

		if (showProfiles) {
			double[] bx = new double[line1[2].length];
			for (int i1 = 0; i1 < line1[2].length; i1++) {
				bx[i1] = (double) i1;
			}

			Plot plot4 = new Plot("Profile", "X Axis", "Y Axis", bx, line1[2]);
			plot4.setLimits(0, bx.length + 10, 0, 300);
			plot4.setSize(400, 200);
			plot4.setColor(Color.red);
			plot4.setLineWidth(2);
			plot4.show();

			if (demo)
				MyLog.waitHere(listaMessaggi(13), debug);

			if (WindowManager.getFrame("Profile") != null) {
				IJ.selectWindow("Profile");
				IJ.run("Close");
			}

			// verifico di avere trovato un max di 2 picchi
			if (peaks1[2].length > 2)
				MyLog.waitHere("Attenzione trovate troppe intersezioni col cerchio, cioè "
						+ peaks1[2].length + "  VERIFICARE");
			if (peaks1[2].length < 2)
				MyLog.waitHere("Attenzione trovata una sola intersezione col cerchio, cioè "
						+ peaks1[2].length + "  VERIFICARE");

			// MyLog.logMatrix(peaks1, "peaks1 " + title);
		}
		return peaks1;
	}

	/**
	 * Disegna una serie di punti nell'overlay di una immagine
	 * 
	 * @param imp1
	 * @param over1
	 * @param peaks1
	 */
	public static void plotPoints(ImagePlus imp1, Overlay over1,
			double[][] peaks1) {
		// MyLog.logMatrix(peaks1, "peaks1");

		float[] xPoints = new float[peaks1[0].length];
		float[] yPoints = new float[peaks1[0].length];

		for (int i1 = 0; i1 < peaks1[0].length; i1++) {
			xPoints[i1] = (float) peaks1[3][i1];
			yPoints[i1] = (float) peaks1[4][i1];
		}

		// MyLog.logVector(xPoints, "xPoints");
		// MyLog.logVector(yPoints, "yPoints");
		imp1.setRoi(new PointRoi(xPoints, yPoints, xPoints.length));
		imp1.getRoi().setStrokeColor(Color.green);
		over1.addElement(imp1.getRoi());
		// MyLog.waitHere("Vedi punti");
	}

	/**
	 * Write preferences into IJ_Prefs.txt
	 * 
	 * @param boundingRectangle
	 */
	public static void writeStoredRoiData(Rectangle boundingRectangle) {

		Prefs.set("prefer.p10rmnDiamFantoc",
				Integer.toString(boundingRectangle.width));
		Prefs.set("prefer.p10rmnXRoi1", Integer.toString(boundingRectangle.x));
		Prefs.set("prefer.p10rmnYRoi1", Integer.toString(boundingRectangle.y));
	}

	/**
	 * Calcolo della distanza tra un punto ed una circonferenza
	 * 
	 * @param x1
	 *            coord. x punto
	 * @param y1
	 *            coord. y punto
	 * @param x2
	 *            coord. x centro
	 * @param y2
	 *            coord. y centro
	 * @param r2
	 *            raggio
	 * @return distanza
	 */
	public static double pointCirconferenceDistance(int x1, int y1, int x2,
			int y2, int r2) {

		double dist = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
				- r2;
		return dist;
	}

}
