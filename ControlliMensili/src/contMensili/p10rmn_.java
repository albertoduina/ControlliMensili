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
import ij.io.FileSaver;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.RankFilters;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyCircleDetector;
import utils.MyConst;
import utils.MyFilter;
import utils.MyFwhm;
import utils.MyInput;
import utils.MyLine;
import utils.MyLog;
import utils.MyMsg;
import utils.MyPlot;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableLimiti;
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

	public static String VERSION = "UNIFORMITA', SNR, FWHM per le bobine array circolari automatico";

	private String TYPE = " >> CONTROLLO SUPERFICIALI UNCOMBINED_";

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.txt
	 */
	private static String fileDir = "";

	private static String simulataName = "";

	private static final boolean debug = true;

	private static int timeout = 0;

	public void run(String args) {

		UtilAyv.setMyPrecision();

		Count c1 = new Count();
		if (!c1.jarCount("iw2ayv_"))
			return;

		String className = this.getClass().getName();

		VERSION = className + "_build_" + MyVersion.getVersion() + "_iw2ayv_build_" + MyVersionUtils.getVersion();

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
	 * FAST: IMMAGINE A DISPLAY, MOSTRATA SOLO POSIZIONE ROI IN OVERLAY, NESSUNA
	 * INTERAZIONE CON L'UTENTE
	 * 
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
	 * QUESTA DESCRIZIONE VA AGGIUNTO CHE SE ANCHE UNO SOLO DEI PARAMETRI VA AL
	 * DI FUORI DAI LIMITI DI MASSIMA:
	 * 
	 * VIENE PRESENTATO UN MESSAGGIO CHE INDICA CHE VALORE SUPERA I LIMITI, CI
	 * SONO 3 POSSIBILITA' DI SCELTA: CONTINUA, VISUALIZZA, SUCCESSIVA CONTINUA
	 * PERMETTE DI CONTINUARE, COME SE NON CI FOSSE STATO ALCUN SUPERAMENTO,
	 * VISUALIZZA LO SI UTILIZZA SE SI E' IN MODO FAST, RIPARTE CON IL
	 * CONTROLLO, PERO'LE IMMAGINI VENGONO VISUALIZZATE, PERMETTENDO DI
	 * LOCALIZZARE (FORSE) IL PROBLEMA, SUCCESSIVA PASSA ALLA IMMAGINE
	 * SUCCESSIVA, I RISULATATI NON VERRANNO SCRITTI
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
		String[] titolo = { "Controllo Uniformit�", "con save UNCOMBINED e ", "immagini circolari" };
		int mode = 0;
		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox()
				// .about("Controllo Uniformit�, con save UNCOMBINED e immagini
				// circolari",
				// this.getClass());
				new AboutBox().about(titolo, MyVersion.CURRENT_VERSION);

				retry = true;
				break;
			case 3:
				timeout = MyInput.myIntegerInput("Ritardo avanzamento (0 = infinito)", "      [msec]", 1000, 0);

				selfTestMenu();
				retry = true;

				break;
			case 4:
				// step = true;
				mode = 3;
			case 5:
				if (mode == 0)
					mode = 2;
				String path1 = UtilAyv.imageSelection("SELEZIONARE PRIMA IMMAGINE...");
				if (path1 == null)
					return 0;
				String path2 = UtilAyv.imageSelection("SELEZIONARE SECONDA IMMAGINE...");
				if (path2 == null)
					return 0;
				// boolean autoCalled = false;
				// boolean verbose = true;
				// boolean test = false;
				double profond = 30;
				// boolean silent = false;
				mainUnifor(path1, path2, "0", profond, "", mode, 0);

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

		boolean fast = Prefs.get("prefer.fast", "false").equals("true") ? true : false;

		int mode = 0;
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

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);

		String info10 = (vetRiga[0] + 1) + " / " + TableSequence.getLength(iw2ayvTable) + "   code= "
				+ TableSequence.getCode(iw2ayvTable, vetRiga[0]) + "   coil= "
				+ TableSequence.getCoil(iw2ayvTable, vetRiga[0]);

		String path1 = "";
		String path2 = "";
		String path3 = "";
		String path4 = "";
		// TableUtils.dumpTableRow(iw2ayvTable, vetRiga[0]);
		boolean ok = false;

		if (nTokens == MyConst.TOKENS2) {
			// UtilAyv.checkImages(vetRiga, iw2ayvTable, 2, debug);

			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			ok = UtilAyv.checkImages2(path1, path2, debug);
			if (ok) {
				MyLog.appendLog(fileDir + "MyLog.txt", "fallito checkImages2");
			}

			MyLog.logDebug(vetRiga[0], "P10", fileDir);
			MyLog.logDebug(vetRiga[1], "P10", fileDir);
		} else {

			// UtilAyv.checkImages(vetRiga, iw2ayvTable, 3, debug);

			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
			path3 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			path4 = TableSequence.getPath(iw2ayvTable, vetRiga[3]);
			ok = UtilAyv.checkImages4(path1, path2, path3, path4, debug);
			if (ok) {
				MyLog.appendLog(fileDir + "MyLog.txt", "fallito checkImages4");
			}

			MyLog.logDebug(vetRiga[0], "P10", fileDir);
			MyLog.logDebug(vetRiga[2], "P10", fileDir);
		}

		// boolean step = false;
		boolean retry = false;
		double profond = readDouble(TableSequence.getProfond(iw2ayvTable, vetRiga[0]));
		if (UtilAyv.isNaN(profond)) {
			MyLog.logVector(iw2ayvTable[vetRiga[0]], "stringa");
			MyLog.waitHere();
		}

		if (fast) {
			mode = 1;
			retry = false;
			// boolean autoCalled = true;
			// boolean verbose = false;
			// boolean test = false;
			// boolean silent = false;
			// MyLog.waitHere(TableSequence.getCode(iw2ayvTable, vetRiga[0])
			// + " " + TableSequence.getCoil(iw2ayvTable, vetRiga[0])
			// + " " + (vetRiga[0] + 1) + " / "
			// + TableSequence.getLength(iw2ayvTable));

			ResultsTable rt = mainUnifor(path1, path2, autoArgs, profond, info10, mode, 0);
			if (rt == null)
				MyLog.waitHere("ResultsTable == null");

			UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);

			UtilAyv.afterWork();

		} else
			do {
				int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));

				switch (userSelection1) {
				case ABORT:
					new AboutBox().close();
					return 0;
				case 2:
					// new AboutBox()
					// .about("Controllo Bobine Array, immagine circolare
					// UNCOMBINED",
					// this.getClass());
					new AboutBox().about("Controllo Bobine Array, immagine circolare UNCOMBINED",
							MyVersion.CURRENT_VERSION);
					retry = true;
					break;
				case 3: // passo
					mode = 3;
				case 4: // AUTO (in realt� MANUAL)
					retry = false;
					if (mode == 0)
						mode = 2;
					//
					// boolean autoCalled = true;
					// boolean verbose = true;
					// boolean test = false;
					// boolean silent = false;

					// double profond = Double.parseDouble(TableSequence
					// .getProfond(iw2ayvTable, vetRiga[0]));

					ResultsTable rt = mainUnifor(path1, path2, autoArgs, profond, info10, mode, 0);

					// public static ResultsTable mainUnifor(String path1,
					// String path2,
					// String autoArgs, double profond, String info10, int mode,
					// int timeout) {

					UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);

					UtilAyv.afterWork();
					break;
				}
			} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	/**
	 * Main per il calcolo dell'uniformita' per bobine di superficie
	 * 
	 * 
	 * introdotto il parametro integer mode, per riunire i flag booleani: step,
	 * verbose, test, fast-----------------------------------------------------
	 * mode = 0 silent ------------------------------------- ------------------
	 * mode = 1 auto fast ----------------------------------------------------
	 * mode = 2 manuale-------------------------------------------------------
	 * mode = 3 manuale passo per passo (step)--------------------------------
	 * mode = 4 --------------------------------------------------------------
	 * mode = 10 test automatico con immagini Siemens o Ge--------------------
	 * 
	 * 
	 * 
	 * 
	 * NOTA 14 GIUGNO 2015 vedo che per 5 modi diversi di fiunzionamento: FAST
	 * MANUALE STEP SILENT abbiamo 5 o 6 flag booleani, che per� non vengono
	 * interpretati in modo uniforme e soprattutto non tutti vengono passati
	 * alle subroutines, generando notevole confusione, quando si desidera
	 * uniformare il modo di funzionamentro dei vari programmi. LA DOMANDA E':
	 * CHE FARE ??????, quasi quasi potrei adottare un integer che per�
	 * rappresenter� i diversi modi di funzionamento 0,1,2,3 ecc oppure potrebbe
	 * nuovamente mimare i binari 0 1 2 4 8 16 32 ecc??? Da una prima occhiata
	 * mi sembrerebbe il caso di mantenere autocalled (viene passato a molte
	 * subroutines)
	 * 
	 * @param path1
	 *            path prima immagine
	 * @param path2
	 *            path seconda immagine
	 * @param autoArgs
	 *            argomentoi ricevuti dalla chiamata
	 * @param profond
	 *            profondit� a cui porre la ROI
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
	public static ResultsTable mainUnifor(String path1, String path2, String autoArgs, double profond, String info10,
			int mode, int timeout) {

		boolean accetta = false;
		// boolean abort = false;
		// boolean demo = false;
		boolean autoCalled = false;
		boolean step = false;
		boolean verbose = false;
		boolean test = false;
		boolean fast = false;
		boolean silent = false;

		switch (mode) {
		case 0:
			// questo e' il caso del funzionamento silent: niente a display
			silent = true;
			break;
		case 1:
			// questo e' il caso del funzionamento fast: tutto va via liscio,
			// solo il minimo sindacale a display
			autoCalled = true;
			fast = true;
			break;
		case 2:
			// questo e' il modo di funzionamento manuale
			verbose = true;
			step = true;
			break;
		case 3:
			// questo e' il modo di funzionamento manuale passo per passo conil
			// valore di tutte le operazioni intermedie
			verbose = true;
			step = true;
			break;
		case 4:
			verbose = true;
			break;
		case 10:
			verbose = true;
			test = true;
			break;
		}

		// boolean broken = false;
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		double angle = Double.NaN;

		//
		// IL VERBOSE FORZATO A TRUE, QUI DI SEGUITO, SERVE
		// A MOSTRARE, PER UN ISTANTE, DOVE VIENE POSIZIONATA AUTOMATICAMENTE
		// LA ROI, anche in FAST.
		//

		// MyLog.waitHere("fast= " + fast + " silent=" + silent + " verbose= "
		// + verbose);

		// String[][] limiti = new InputOutput().readFile6("LIMITI.csv");

		String[][] limiti = TableLimiti.loadTable(MyConst.LIMITS_FILE);

		double[] vetMinimi = UtilAyv.doubleLimiti(UtilAyv.decoderLimiti(limiti, "P10MIN"));
		// MyLog.logVector(vetMinimi, "vetMinimi");
		double[] vetMaximi = UtilAyv.doubleLimiti(UtilAyv.decoderLimiti(limiti, "P10MAX"));
		// MyLog.logVector(vetMaximi, "vetMaximi");
		// MyLog.waitHere();

		// ===============================================================
		// ATTENZIONE: questi sono solo valori di default utilizzati in
		// assenza di limiti.csv
		// ================================================================
		double minMean7x7 = +10;
		double maxMean7x7 = +4096;
		double minMeanBkg = -2048;
		double maxMeanBkg = +2048;
		double minSnRatio = +333;
		double maxSnRatio = +444;

		double minFWHM = 0;
		double maxFWHM = +512;

		// ================================================================
		if (vetMinimi == null) {
			MyLog.waitHere(listaMessaggi(65), debug);
		} else {
			minMean7x7 = vetMinimi[0];
			minMeanBkg = vetMinimi[1];
			minSnRatio = vetMinimi[2];
			minFWHM = vetMinimi[3];
		}
		if (vetMaximi == null) {
			MyLog.waitHere(listaMessaggi(66), debug);
		} else {
			maxMean7x7 = vetMaximi[0];
			maxMeanBkg = vetMaximi[1];
			maxSnRatio = vetMaximi[2];
			maxFWHM = vetMaximi[3];
		}

		do {
			ImagePlus imp11 = null;
			if (verbose)
				imp11 = UtilAyv.openImageMaximized(path1);
			else
				imp11 = UtilAyv.openImageNoDisplay(path1, true);

			if (imp11 == null)
				MyLog.waitHere("Non trovato il file " + path1);
			// ImageWindow iw11 = WindowManager.getCurrentWindow();
			double out2[] = positionSearch(imp11, profond, info10, mode, timeout);

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
			// se anche uno solo dei check limits � fallito si deve tornare
			// qui
			// ed eseguire il controllo, come minimo senza fast attivo ed in
			// modalit� verbose
			// ========================================================================
			ImagePlus imp1 = null;
			ImagePlus imp2 = null;
			ImageWindow iw1 = null;
			// MyLog.waitHere();
			// if (verbose && !silent) {
			if (!silent) {
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

			String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);

			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp1, tabCodici, VERSION
					+ "_P10__ContMensili_" + MyVersion.CURRENT_VERSION + "__iw2ayv_" + MyVersionUtils.CURRENT_VERSION,
					autoCalled);

			//
			rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);

			// ============================================================================
			// Fine calcoli geometrici
			// Inizio calcoli Uniformit�
			// ============================================================================

			Overlay over2 = new Overlay();
			Overlay over3 = new Overlay();
			// Overlay over4 = new Overlay();

			int sqNEA = MyConst.P10_NEA_11X11_PIXEL;
			// disegno MROI gi� predeterminata
			imp1.setOverlay(over2);
			int xCenterRoi = (int) out2[0];
			int yCenterRoi = (int) out2[1];
			int xCenterCircle = (int) out2[2];
			int yCenterCircle = (int) out2[3];
			// int xMaxima = (int) out2[4];
			// int yMaxima = (int) out2[5];
			angle = out2[6];
			int xBordo = (int) out2[7];
			int yBordo = (int) out2[8];

			int width = imp1.getWidth();
			int height = imp1.getHeight();
			double dimPixel = ReadDicom.readDouble(
					ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));
					// MyLog.waitHere();

			// =================================================
			// Questo � l'overlay che si vede in fast

			if (fast || verbose) {
				// MyLog.waitHere();
				// =================================================
				// Centro cerchio
				MyCircleDetector.drawCenter(imp1, over2, xCenterCircle, yCenterCircle, Color.red);

				MyCircleDetector.drawCenter(imp1, over2, xBordo, yBordo, Color.pink);

				imp1.setRoi(new Line(xCenterCircle, yCenterCircle, xBordo, yBordo));
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.green);

				imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA, sqNEA);

				imp1.getRoi().setStrokeColor(Color.green);
				imp1.getRoi().setStrokeWidth(1.1);
				over2.addElement(imp1.getRoi());

				imp1.updateAndDraw();
				imp1.deleteRoi();

			}
			// =================================================

			// imp1.getRoi().setName("NEA");
			// int indexNEA = over2.getIndex("NEA");

			// MyLog.waitHere("Disegnata MROI xCenterRoi = " + xCenterRoi
			// + " yCenterRoi= " + yCenterRoi);
			if (step)
				MyLog.waitHere(listaMessaggi(30), debug);
			//
			// posiziono la ROI 7x7 all'interno di MROI
			//
			int sq7 = MyConst.P10_MROI_7X7_PIXEL;
			imp1.setRoi(xCenterRoi - sq7 / 2, yCenterRoi - sq7 / 2, sq7, sq7);
			// int indexMROI = 0;
			if (verbose) {
				imp1.getRoi().setStrokeColor(Color.red);
				imp1.getRoi().setStrokeWidth(1.1);
				over2.addElement(imp1.getRoi());
				imp1.getRoi().setName("MROI");
				// indexMROI = over2.getIndex("MROI");

			}

			imp1.updateAndDraw();
			if (step)
				MyLog.waitHere(listaMessaggi(31), debug);

			ImageStatistics stat7x7 = imp1.getStatistics();
			// MyLog.waitHere();

			// =============================================================

			double xBkg = imp1.getWidth() - MyConst.P10_X_ROI_BACKGROUND;
			double yBkg = MyConst.P10_Y_ROI_BACKGROUND;
			boolean irraggiungibile = false;
			int diamBkg = MyConst.P10_DIAM_ROI_BACKGROUND;
			int guard = 10;
			boolean circle = true;
			int select = 1;

			// MyLog.waitHere("step= " + step + " verbose= " + verbose +
			// " test= "
			// + test + " fast= " + fast + " silent= " + silent);

			// double[] backPos = UtilAyv.positionSearch15(imp1, out2, xBkg,
			// yBkg,
			// diamBkg, guard, mode, info10, circle, autoCalled, step,
			// demo, test, fast, irraggiungibile);

			// public static double[] positionSearch15(ImagePlus imp1,
			// double[] circleData, double xBkg, double yBkg, double diamBkg,
			// int guard, int select, String info1, boolean circle, int mode,
			// boolean irraggiungibile) {

			double[] backPos = UtilAyv.positionSearch15(imp1, out2, xBkg, yBkg, diamBkg, guard, select, info10, circle,
					mode, irraggiungibile);

			xBkg = backPos[0] - diamBkg / 2;
			yBkg = backPos[1] - diamBkg / 2;

			//
			// disegno RoiFondo su imp1
			//

			imp1.setRoi(new OvalRoi((int) xBkg, (int) yBkg, (int) diamBkg, (int) diamBkg));
			ImageStatistics statBkg = imp1.getStatistics();

			// if (verbose) {
			ImageUtils.addOverlayRoi(imp1, Color.yellow, 1.1);

			// imp1.getRoi().setStrokeColor(Color.yellow);
			// imp1.getRoi().setStrokeWidth(1.1);
			// over2.addElement(imp1.getRoi());
			// }

			if (verbose)
				MyLog.waitHere(listaMessaggi(26) + statBkg.mean, debug, timeout);

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
			ImagePlus impDiff = UtilAyv.genImaDifference(imp1, imp2);
			// if (verbose && !fast && demo) {
			if (verbose && !fast) {
				UtilAyv.showImageMaximized(impDiff);
				MyLog.waitHere(listaMessaggi(27), debug, timeout);

			}
			impDiff.setOverlay(over3);
			if (verbose && !fast) {
				// =================================================

				MyCircleDetector.drawCenter(impDiff, over3, xCenterCircle, yCenterCircle, Color.red);

				MyCircleDetector.drawCenter(impDiff, over3, xBordo, yBordo, Color.pink);

				imp1.setRoi(new Line(xCenterCircle, yCenterCircle, xBordo, yBordo));
				over3.addElement(imp1.getRoi());
				over3.setStrokeColor(Color.green);
				impDiff.killRoi();

				// =================================================
			}

			impDiff.resetDisplayRange();
			impDiff.setRoi(xCenterRoi - sq7 / 2, yCenterRoi - sq7 / 2, sq7, sq7);
			impDiff.getRoi().setStrokeColor(Color.red);
			impDiff.getRoi().setStrokeWidth(1.1);
			over3.addElement(impDiff.getRoi());
			ImageStatistics statImaDiff = impDiff.getStatistics();
			if (impDiff.isVisible())
				ImageUtils.imageToFront(impDiff);

			if (verbose)
				MyLog.waitHere(listaMessaggi(33), debug, timeout);

			//
			// calcolo P su imaDiff
			//
			double prelimImageNoiseEstimate_7x7 = statImaDiff.stdDev / Math.sqrt(2);

			if (step)
				MyLog.waitHere(listaMessaggi(34) + statImaDiff.stdDev
						+ "\npreliminaryNoiseEstimate= stdDev / sqrt(2) = " + +prelimImageNoiseEstimate_7x7, debug);

			if (imp1.isVisible())
				ImageUtils.imageToFront(imp1);

			//
			// loop di calcolo NEA su imp1
			//
			imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA, sqNEA);

			// ==================================================================
			// qui, se il numero dei pixel < 121 dovr� incrementare sqR2 e
			// ripetere il loop
			// ==================================================================

			double checkPixelsLimit = MyConst.P10_CHECK_PIXEL_MULTIPLICATOR * prelimImageNoiseEstimate_7x7;
			int area11x11 = MyConst.P10_NEA_11X11_PIXEL * MyConst.P10_NEA_11X11_PIXEL;

			// area11x11 = 2000;
			int enlarge = 0;
			int pixx = 0;

			do {

				boolean paintPixels = !fast;
				// boolean paintPixels = true;

				pixx = countPixOverLimitCentered(imp1, xCenterRoi, yCenterRoi, sqNEA, checkPixelsLimit, paintPixels,
						over2);

				imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA, sqNEA);
				imp1.updateAndDraw();

				if (pixx < area11x11) {
					sqNEA = sqNEA + 2; // accrescimento area
					enlarge = enlarge + 1;
				}
				// if (step)
				// MyLog.waitHere(listaMessaggi(35) + sqNEA, debug);

				// verifico che quando cresce il lato del quadrato non si
				// esca
				// dall'immagine

				if ((xCenterRoi + sqNEA - enlarge) >= width || (xCenterRoi - enlarge) <= 0) {
					MyLog.waitHere(listaMessaggi(32), debug, timeout);
					// return null;
					// broken = true;
					return rt;
				}
				if ((yCenterRoi + sqNEA - enlarge) >= height || (yCenterRoi - enlarge) <= 0) {
					MyLog.waitHere(listaMessaggi(32), debug, timeout);
					// return null;
					// broken = true;
					return rt;
				}
				if (step && pixx >= area11x11)
					MyLog.waitHere(listaMessaggi(22) + pixx, debug, timeout);

			} while (pixx < area11x11);

			imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA, sqNEA);
			imp1.updateAndDraw();

			//
			// calcolo SD su imaDiff quando i corrispondenti pixel
			// di imp1 passano il test
			//

			// qui era il problema devStandardNema non era centered e quindi
			// faceva il quadrato spostato

			boolean paintPixels = true;
			double[] out11 = devStandardNemaCentered(imp1, impDiff, xCenterRoi, yCenterRoi, sqNEA, checkPixelsLimit,
					paintPixels, over2);
			if (step)
				MyLog.waitHere(listaMessaggi(23) + out11[0] + "stdDev4= " + out11[1], debug, timeout);
			//
			// calcolo SNR finale
			//
			double finalSnr = stat7x7.mean / (out11[1] / Math.sqrt(2));

			if (out11[1] == 0) {
				MyLog.waitHere("out11 = 0");
				finalSnr = 100;
			}
			if (step)
				MyLog.waitHere(listaMessaggi(24) + finalSnr, debug);

			String patName = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PATIENT_NAME);

			String codice1 = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SERIES_DESCRIPTION);

			String codice = UtilAyv.getFiveLetters(codice1);

			simulataName = fileDir + patName + codice + "sim.zip";

			// passo due volte step (al posto di verbose) per non vedere la
			// simulata in fast
			int[][] classiSimulata = ImageUtils.generaSimulata12classi(xCenterRoi, yCenterRoi, sq7, imp1, simulataName,
					mode, timeout);

			//
			// calcolo posizione fwhm a met� della MROI
			//

			ImageUtils.imageToFront(iw1);

			//
			// ----------------------------------------------------------
			// Calcolo FWHM
			// la direzione su cui verr� preso il profilo � quella centro
			// ROI - centro cerchio
			// -----------------------------------------------------------
			//

			double[] out3 = ImageUtils.crossingFrame(xCenterRoi, yCenterRoi, xCenterCircle, yCenterCircle, width,
					height);

			// aveva restituito null
			if (out3 == null)
				MyLog.waitHere("out3==null");

			// ora per� devo riordinare i valori restituiti da crossing, in
			// modo
			// che il punto di start del profilo sia quello pi� vicino al
			// centro
			// ROI.

			double dist1 = MyFwhm.lengthCalculation(out3[0], out3[1], xCenterRoi, yCenterRoi);
			double dist2 = MyFwhm.lengthCalculation(out3[2], out3[3], xCenterRoi, yCenterRoi);
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
				imp1.setRoi(new Line(xStartProfile, yStartProfile, xEndProfile, yEndProfile));
				imp1.updateAndDraw();
			}

			IJ.wait(MyConst.TEMPO_VISUALIZZ);

			double[] profile2 = getProfile(imp1, xStartProfile, yStartProfile, xEndProfile, yEndProfile, dimPixel,
					step);
			double[] outFwhm2 = MyFwhm.analyzeProfile(profile2, dimPixel, codice, false, verbose);

			if (verbose)
				MyLog.waitHere(listaMessaggi(28), debug, timeout);

			// =================================================================
			double slicePosition = ReadDicom
					.readDouble(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SLICE_LOCATION));

			// =================================================================
			// Effettuo dei controlli "di sicurezza" sui valori calcolati,
			// in modo da evitare possibili sorprese
			// ================================================================

			UtilAyv.checkLimits2(stat7x7.mean, minMean7x7, maxMean7x7, "SEGNALE ROI 7X7");
			UtilAyv.checkLimits2(statBkg.mean, minMeanBkg, maxMeanBkg, "RUMORE FONDO");
			UtilAyv.checkLimits2(finalSnr, minSnRatio, maxSnRatio, "FINAL SNR RATIO");
			UtilAyv.checkLimits2(outFwhm2[0], minFWHM, maxFWHM, "FWHM");

			//
			// Salvataggio dei risultati nella ResultsTable
			//

			// int col = 2;

			String t1 = "TESTO";
			String s2 = "VALORE";
			String s3 = "roi_x";
			String s4 = "roi_y";
			String s5 = "roi_b";
			String s6 = "roi_h";

			rt.addLabel(t1, simulataName);
			rt.incrementCounter();

			rt.addLabel(t1, "Segnale");
			rt.addValue(s2, stat7x7.mean);
			// rt.addValue(3, xCenterRoi);
			// rt.addValue(4, yCenterRoi);
			rt.addValue(s3, stat7x7.roiX);
			rt.addValue(s4, stat7x7.roiY);
			rt.addValue(s5, stat7x7.roiWidth);
			rt.addValue(s6, angle);

			rt.incrementCounter();
			rt.addLabel(t1, "Rumore_Fondo");
			rt.addValue(s2, (out11[1] / Math.sqrt(2)));
			rt.addValue(s3, statBkg.roiX);
			rt.addValue(s4, statBkg.roiY);
			rt.addValue(s5, statBkg.roiWidth);
			rt.addValue(s6, statBkg.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "SnR");
			rt.addValue(s2, finalSnr);
			rt.addValue(s3, stat7x7.roiX);
			rt.addValue(s4, stat7x7.roiY);
			rt.addValue(s5, stat7x7.roiWidth);
			rt.addValue(s6, stat7x7.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "FWHM");
			rt.addValue(s2, outFwhm2[0]);
			rt.addValue(s3, xStartProfile);
			rt.addValue(s4, yStartProfile);
			rt.addValue(s5, xEndProfile);
			rt.addValue(s6, yEndProfile);

			rt.incrementCounter();
			rt.addLabel(t1, "Bkg");
			rt.addValue(s2, statBkg.mean);
			rt.addValue(s3, statBkg.roiX);
			rt.addValue(s4, statBkg.roiY);
			rt.addValue(s5, statBkg.roiWidth);
			rt.addValue(s6, statBkg.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Pos");
			rt.addValue(s2, slicePosition);

			String[] levelString = { "+20%", "+10%", "-10%", "-10%", "-30%", "-40%", "-50%", "-60%", "-70%", "-80%",
					"-90%", "fondo" };

			for (int i1 = 0; i1 < classiSimulata.length; i1++) {
				rt.incrementCounter();
				rt.addLabel(t1, ("Classe" + classiSimulata[i1][0]) + "_" + levelString[i1]);
				rt.addValue(s2, classiSimulata[i1][1]);
			}

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
		String[] path = new InputOutput().findListTestImages2(MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		String path1 = path[0];
		String path2 = path[1];

		String autoArgs = "0";
		double profond = 30;

		int mode;
		if (verbose)
			mode = 10; // modalit� demo
		else
			mode = 0; // modalit� silent

		ResultsTable rt1 = mainUnifor(path1, path2, autoArgs, profond, "", mode, timeout);

		// rt1.show("Results");
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		// MyLog.logVector(vetResults, "vetResults");
		// MyLog.logVector(vetReference, "vetReference");
		// MyLog.waitHere();
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference, MyConst.P10_vetName);
		if (verbose)
			UtilAyv.afterWork();

		return ok;
	}

	/**
	 * Siemens test image expected results
	 * 
	 * @return
	 */
	public static double[] referenceSiemens() {

		// 0.0, 355.6938775510204, 10.063527551353923, 35.3448505741077,
		// 24.264691013919478, 12.4375, 0.0, 2763.0, 1478.0, 7761.0, 2712.0,
		// ,

		double simul = 0.0;
		double signal = 354.16326530612247;
		// double backNoise = 12.225;
		double backNoise = 10.224592241325686;
		double snRatio = 34.638375491852656;
		// double fwhm = 11.43429317989865;
		double fwhm = 24.329842905555697;
		double bkg = 12.4375;
		double pos = 0.0;
		double num1 = 2818.0;
		double num2 = 1526.0;
		double num3 = 7834.0;
		double num4 = 2607.0;
		double num5 = 2568.0;
		double num6 = 2672.0;
		double num7 = 2969.0;
		double num8 = 3388.0;
		double num9 = 3229.0;
		double num10 = 1768.0;
		double num11 = 341.0;
		double num12 = 33816.0;

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm, bkg, pos, num1, num2, num3, num4, num5, num6,
				num7, num8, num9, num10, num11, num12 };
		return vetReference;

	}

	// selfTestSilent.p10rmn_
	// SIGNAL ERRATO 354.16326530612247 anzichè 355.6938775510204
	// BACKNOISE ERRATO 10.224592241325686 anzichè 11.731812354423619
	// SNRATIO ERRATO 34.638375491852656 anzichè 30.318749295108
	// FWHM ERRATO 24.329842905555697 anzichè 24.264691013919478
	// NUM_CLASS1 ERRATO 2818.0 anzichè 2763.0
	// NUM_CLASS2 ERRATO 1526.0 anzichè 1478.0
	// NUM_CLASS3 ERRATO 7834.0 anzichè 7761.0
	// NUM_CLASS4 ERRATO 2607.0 anzichè 2712.0
	// NUM_CLASS5 ERRATO 2568.0 anzichè 2574.0
	// NUM_CLASS6 ERRATO 2672.0 anzichè 2652.0
	// NUM_CLASS7 ERRATO 2969.0 anzichè 3054.0
	// NUM_CLASS8 ERRATO 3388.0 anzichè 3284.0
	// NUM_CLASS9 ERRATO 3229.0 anzichè 3333.0
	// NUM_CLASS10 ERRATO 1768.0 anzichè 1755.0
	// NUM_CLASS11 ERRATO 341.0 anzichè 354.0
	// Il test di p10rmn_ UNIFORMITA' SUPERFICIALE evidenzia degli ERRORI

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
		double bkg = 12.4375;
		double pos = 0.0;
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

		double[] vetReference = { simul, signal, backNoise, snRatio, fwhm, bkg, pos, num1, num2, num3, num4, num5, num6,
				num7, num8, num9, num10, num11, num12 };
		return vetReference;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {
		boolean verbose = false;
		boolean ok = selfTestSiemens(verbose);
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
	 *            soglia di conteggio, vengono contati i pixel che la superano
	 * @param paintPixels
	 *            switch per test, se attivato vengono colorati i pixels di cui
	 *            viene effettuato il conteggio. Utilizzato per verificare che
	 *            le varie ROI siano posizionate correttamente
	 * @return pixel che superano la soglia
	 */
	public static int countPixOverLimitCentered(ImagePlus imp1, int sqX, int sqY, int sqR, double limit,
			boolean paintPixels, Overlay over1) {
		int offset = 0;
		int w = 0;
		int count1 = 0;

		if (imp1 == null) {
			IJ.error("CountPixTest ricevuto null");
			return (0);
		}

		// MyLog.waitHere("sqX= "+sqX+" sqY= "+sqY+" sqR= "+sqR);
		int width = imp1.getWidth();
		short[] pixels1 = UtilAyv.truePixels(imp1);

		boolean ok = false;
		for (int y1 = sqY - sqR / 2; y1 <= (sqY + sqR / 2); y1++) {
			offset = y1 * width;
			for (int x1 = sqX - sqR / 2; x1 <= (sqX + sqR / 2); x1++) {
				w = offset + x1;
				ok = false;
				if (w >= 0 && w < pixels1.length && pixels1[w] > limit) {
					ok = true;
					count1++;
				} else
					ok = false;
				if (paintPixels)
					setOverlayPixel(over1, imp1, x1, y1, Color.green, Color.red, ok);
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
	 * @param sqX1
	 *            coordinata del centro Roi
	 * @param sqY1
	 *            coordinata del centro Roi
	 * @param sqR
	 *            lato della Roi
	 * @param limit
	 *            soglia di conteggio
	 * @param paintPixels
	 *            marcatura Roi su overlay grafico
	 * @param over1
	 *            overlay grafico
	 * @return [0] sum / pixelcount [1] devStan
	 */

	private static double[] devStandardNemaCentered(ImagePlus imp1, ImagePlus imp3, int sqX1, int sqY1, int sqR,
			double limit, boolean paintPixels, Overlay over1) {
		double[] results = new double[2];
		double value4 = 0.0;
		double sumValues = 0.0;
		double sumSquare = 0.0;
		boolean ok;

		// modifica del 260216
		int sqX = sqX1 - sqR / 2;
		int sqY = sqY1 - sqR / 2;
		// --------

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
				ok = false;
				offset = y1 * width + x1;
				// IJ.log("offset= " + offset + " y1= " + y1 + " width= " +
				// width
				// + " x1= " + x1);
				if (pixels1[offset] > limit) {
					pixelCount++;
					value4 = pixels4[offset];
					sumValues += value4;
					sumSquare += value4 * value4;
					ok = true;
				}
				// modifica del 260216
				if (paintPixels)
					setOverlayPixel(over1, imp1, x1, y1, Color.yellow, Color.green, ok);
				// --------

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

	private static double[] getProfile(ImagePlus imp1, int ax, int ay, int bx, int by, double dimPixel, boolean step) {

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

		int diam = ReadDicom.readInt(Prefs.get("prefer.p10rmnDiamFantoc", Integer.toString(width * 2 / 3)));
		int xRoi1 = ReadDicom.readInt(Prefs.get("prefer.p10rmnXRoi1", Integer.toString(height / 2 - diam / 2)));
		int yRoi1 = ReadDicom.readInt(Prefs.get("prefer.p10rmnYRoi1", Integer.toString(width / 2 - diam / 2)));
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
	 * coordinata Y ha lo 0 in alto a sx, anzich� in basso a sx, come siamo
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
		double dy = by - ay; // dy � all'incontrario, per le coordinate di
								// ImageJ
		double theta = Math.atan2(dy, dx);
		return theta;

	}

	/**
	 * Calcola le coordinate del centro ROI sul segmento circonferenza - centro,
	 * alla profondit� desiderata
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
	 *            profondit� centro ROI
	 * @return vettore coordinate centro ROI
	 */
	public static double[] interpolaProfondCentroROI(double ax, double ay, double bx, double by, double prof) {

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
	public static double linearInterpolation(double x0, double y0, double x1, double y1, double x2) {

		double y2 = y0 + ((x2 - x0) * y1 - (x2 - x0) * y0) / (x1 - x0);

		return y2;
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
	public static double[][] profileAnalyzer(ImagePlus imp1, double dimPixel, String title, boolean showProfiles,
			boolean vertical, int timeout) {

		// MyLog.waitHere("showProfiles= " + showProfiles);

		double[][] profi3 = MyLine.decomposer(imp1);

		double[] vetz = new double[profi3[0].length];
		for (int i1 = 0; i1 < profi3[0].length; i1++) {
			vetz[i1] = profi3[2][i1];
		}
		// double[] minmax = Tools.getMinMax(vetz);

		ArrayList<ArrayList<Double>> matOut = null;
		double[][] peaks1 = null;

		// double limit = minmax[1] / 20;
		// if (limit < 100)
		// limit = 100;
		double limit = 100;

		do {
			matOut = ImageUtils.peakDet1(profi3, limit);
			peaks1 = new InputOutput().fromArrayListToDoubleTable(matOut);
			if (peaks1 == null) {
				// MyLog.waitHere("peaks1 == null");
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
			if (peaks1[0].length > 2)
				limit = limit + limit * 0.1;
		} while (peaks1[0].length > 2);

		double[] xPoints = new double[peaks1[0].length];
		double[] yPoints = new double[peaks1[0].length];
		double[] zPoints = new double[peaks1[0].length];
		for (int i1 = 0; i1 < peaks1[0].length; i1++) {
			xPoints[i1] = peaks1[0][i1];
			yPoints[i1] = peaks1[1][i1];
			zPoints[i1] = peaks1[2][i1];
		}

		if (showProfiles) {
			Plot plot2 = MyPlot.basePlot2(profi3, title, Color.GREEN, vertical);
			plot2.draw();
			plot2.setColor(Color.red);
			if (vertical)
				plot2.addPoints(yPoints, zPoints, PlotWindow.CIRCLE);
			else
				plot2.addPoints(xPoints, zPoints, PlotWindow.CIRCLE);
			plot2.show();

			Frame lw = WindowManager.getFrame(title);
			if (lw != null)
				lw.setLocation(10, 10);

			MyLog.waitHere(listaMessaggi(5), debug, timeout);

		}

		if (WindowManager.getFrame(title) != null) {
			IJ.selectWindow(title);
			IJ.run("Close");
		}

		return peaks1;
	}

	/**
	 * Verifica se un valore � all'interno dei limiti assegnati, con una certa
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
	 * @return true se il valore � valido (entro i limiti)
	 */
	public static boolean isBetween(double x1, double low, double high, double tolerance) {

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
		long num1 = io.extractFromJAR2(MyConst.TEST_FILE, "C001_testP10", "./Test2/");
		long num2 = io.extractFromJAR2(MyConst.TEST_FILE, "C002_testP10", "./Test2/");
		if (num1 <= 0 || num2 <= 0)
			MyLog.waitHere("errore estrazione files da test2.jar");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY).getPath();
		return (home1);
	}

	/**
	 * Ricerca della posizione della ROI per il calcolo dell'uniformit�
	 * 
	 * @param imp11
	 *            immagine di input
	 * @param profond
	 *            profondit� ROI
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
	public static double[] positionSearch(ImagePlus imp11, double profond, String info1, int mode, int timeout) {

		// boolean autoCalled=false;
		
		boolean demo = false;
		Color colore1 = Color.red;
		Color colore2 = Color.green;
		Color colore3 = Color.red;
		
		if (mode == 10 || mode==3)
			demo = true;
		// boolean step = false;
		// boolean verbose = false;
		// boolean test = false;
		// boolean fast = false;
		//

		// ================================================================================
		// Inizio calcoli geometrici
		// ================================================================================
		//

		// MyLog.waitHere("autoCalled= " + autoCalled + "\nstep= " + step
		// + "\nverbose= " + verbose + "\ntest= " + test + "\nfast= "
		// + fast);

		boolean manual = false;
		// boolean demo = verbose;
		// boolean showProfiles = demo;
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
		if (imp11 == null)
			MyLog.waitHere("imp11==null");

		double dimPixel = ReadDicom.readDouble(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp11, MyConst.DICOM_PIXEL_SPACING), 1));

		ImageWindow iw11 = null;
		if (demo)
			iw11 = imp11.getWindow();

		int width = imp11.getWidth();
		int height = imp11.getHeight();
		ImagePlus imp12 = imp11.duplicate();
		imp12.setTitle("DUP");

		// ************************************
		// UtilAyv.showImageMaximized(imp12);
		// UtilAyv.showImageMaximized(imp11);
		// ************************************

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
			MyLog.waitHere("L'immagine verra' processata con il filtro variance, per estrarre il bordo", debug, timeout);
		}

		// ip12.findEdges();
		RankFilters rk1 = new RankFilters();
		double radius = 0.1;
		int filterType = RankFilters.VARIANCE;
		rk1.rank(ip12, radius, filterType);
		imp12.updateAndDraw();
		if (demo)
			MyLog.waitHere("L'immagine risultante ha il bordo con il segnale fortemente evidenziato", debug, timeout);

		// =============== modifica 290515 ===========
		double max1 = imp12.getStatistics().max;
		ip12.subtract(max1 / 30);
		// ===========================================

		imp12.updateAndDraw();
		if (demo)
			MyLog.waitHere(
					"All'intera immagine viene sottratto 1/30 del segnale massimo,\n questo per eliminare eventuale noise residuo",
					debug, timeout);

//		if (demo)
//			MyLog.waitHere(listaMessaggi(3), debug, timeout);

		imp12.setOverlay(over12);

		double[][] myPeaks = new double[4][1];
		int[] myXpoints = new int[16];
		int[] myYpoints = new int[16];

		int[] xcoord = new int[2];
		int[] ycoord = new int[2];
		boolean manualOverride = false;

		int[] vetx0 = new int[8];
		int[] vetx1 = new int[8];
		int[] vety0 = new int[8];
		int[] vety1 = new int[8];

		vetx0[0] = 0;
		vety0[0] = height / 2;
		vetx1[0] = width;
		vety1[0] = height / 2;
		// ----
		vetx0[1] = width / 2;
		vety0[1] = 0;
		vetx1[1] = width / 2;
		vety1[1] = height;
		// ----
		vetx0[2] = 0;
		vety0[2] = 0;
		vetx1[2] = width;
		vety1[2] = height;
		// -----
		vetx0[3] = width;
		vety0[3] = 0;
		vetx1[3] = 0;
		vety1[3] = height;
		// -----
		vetx0[4] = width / 4;
		vety0[4] = 0;
		vetx1[4] = width * 3 / 4;
		vety1[4] = height;
		// ----
		vetx0[5] = width * 3 / 4;
		vety0[5] = 0;
		vetx1[5] = width / 4;
		vety1[5] = height;
		// ----
		vetx0[6] = width;
		vety0[6] = height * 1 / 4;
		vetx1[6] = 0;
		vety1[6] = height * 3 / 4;
		// ----
		vetx0[7] = 0;
		vety0[7] = height * 1 / 4;
		vetx1[7] = width;
		vety1[7] = height * 3 / 4;

		String[] vetTitle = { "orizzontale", "verticale", "diagonale sinistra", "diagonale destra", "inclinata 1",
				"inclinata 2", "inclinata 3", "inclinata 4" };

		// multipurpose line analyzer

		int count = -1;

		int[] xPoints3 = null;
		int[] yPoints3 = null;
		boolean vertical = false;
		boolean valido = true;
		for (int i1 = 0; i1 < 8; i1++) {

			xcoord[0] = vetx0[i1];
			ycoord[0] = vety0[i1];
			xcoord[1] = vetx1[i1];
			ycoord[1] = vety1[i1];
			imp12.setRoi(new Line(xcoord[0], ycoord[0], xcoord[1], ycoord[1]));
			if (demo) {
				imp12.getRoi().setStrokeColor(colore2);
				over12.addElement(imp12.getRoi());
				imp12.updateAndDraw();
			}

			if (i1 == 1)
				vertical = true;
			else
				vertical = false;

			boolean showProfiles = false;

			if (demo && i1 == 0)
				showProfiles = true;

			myPeaks = profileAnalyzer(imp12, dimPixel, vetTitle[i1], showProfiles, vertical, timeout);

			valido = true;
			String direction1 = ReadDicom.readDicomParameter(imp11, MyConst.DICOM_IMAGE_ORIENTATION);
			String direction2 = "1\0\0\01\0";

			if (myPeaks != null) {
				// MyLog.logMatrix(myPeaks, "myPeaks");
				// MyLog.waitHere("profileAnalyzer ritorna questi punti");

				// della bisettice orizzontale prendo solo il picco di dx
				// della bisettice verticale prendo solo il picco in basso
				// in questi due casi, se esiste, prendo solo il secondo picco
				// (a patto di aver tracciato correttamente la bisettrice)
				// if (i1 < 2) {e alla bolla d'aria
				// if (myPeaks[0].length == 2) {
				// count++;
				// myXpoints[count] = (int) (myPeaks[0][1]);
				// myYpoints[count] = (int) (myPeaks[1][1]);
				// }
				// } else {
				// for (int i2 = 0; i2 < myPeaks[0].length; i2++) {
				// count++;
				// myXpoints[count] = (int) (myPeaks[0][i2]);
				// myYpoints[count] = (int) (myPeaks[1][i2]);
				// }
				// }

				// per evitare le bolle d'aria escluderò il punto in alto per
				// l'immagine assiale ed il punto a sinistra dell'immagine
				// sagittale. Considero punto in alto quello con coordinata y <
				// mat/2 e come punto a sinistra quello con coordinata x < mat/2

				for (int i2 = 0; i2 < myPeaks[0].length; i2++) {

					if ((direction1.compareTo("0\\1\\0\\0\\0\\-1") == 0) && (i1 == 0)) {
						if (((int) (myPeaks[0][i2]) < width / 2)) {
							valido = false;
							// MyLog.waitHere("linea orizzontale eliminato punto
							// sx");
						} else
							;
						// MyLog.waitHere("linea orizzontale mantenuto punto
						// dx");
					}

					if ((direction1.compareTo("1\\0\\0\\1\\0") == 0) && (i1 == 1)) {
						if (((int) (myPeaks[1][i2]) < height / 2)) {
							valido = false;
							// MyLog.waitHere("linea verticale eliminato punto
							// sup");
						} else
							;
						// MyLog.waitHere("linea orizzontale mantenuto punto
						// inf");
					}

					if (valido) {
						count++;
						myXpoints[count] = (int) (myPeaks[0][i2]);
						myYpoints[count] = (int) (myPeaks[1][i2]);
						ImageUtils.plotPoints(imp12, over12, (int) (myPeaks[0][i2]), (int) (myPeaks[1][i2]), colore1);
						imp12.updateAndDraw();
						ImageUtils.imageToFront(imp12);
					}
				}
			}

			// devo compattare i vettori myXpoints e myYpoints, ovviamente a
			// patto che count >=0;
		}

		if (demo)
			MyLog.waitHere("Si tracciano ulteriori linee ", debug, timeout);

		
		
		
		if (count >= 0) {
			count++;
			xPoints3 = new int[count];
			yPoints3 = new int[count];

			for (int i3 = 0; i3 < count; i3++) {
				xPoints3[i3] = myXpoints[i3];
				yPoints3[i3] = myYpoints[i3];
			}
		} else {
			xPoints3 = null;
			yPoints3 = null;
		}


		// MyLog.logVector(myXpoints, "myXpoints");
		// MyLog.logVector(xPoints3, "xPoints3");
		// MyLog.logVector(myYpoints, "myYpoints");
		// MyLog.logVector(yPoints3, "yPoints3");
		// MyLog.waitHere("count= " + count);

		// if (myPeaks != null || step) {
		//
		// ImageUtils.plotPoints(imp12, over12, xPoints3, yPoints3);
		// imp12.updateAndDraw();
		// ImageUtils.imageToFront(imp12);
		// MyLog.waitHere("VERIFICA PLOTTAGGIO NUOVO PUNTO");
		// if (test && step)
		// MyLog.waitHere("BISETTRICE " + vetTitle[i1]);
		// }

		// --------------------------------------------
		// if (demo) {
		// MyLog.waitHere(listaMessaggi(12), debug, timeout);
		// }
		// --------------------------------------------

		
		// qui di seguito pulisco l'overlay, dovrò preoccuparmi di ridisegnare i punti
		imp12.deleteRoi();
		over12.clear();
		imp12.updateAndDraw();

		// ----------------------------------------------------------------------
		// Verifica di avere trovato almeno 3 punti, altrimenti chiede la
		// selezione manuale del cerchio
		// -------------------------------------------------------------------

		if (xPoints3 == null || xPoints3.length < 3) {
			UtilAyv.showImageMaximized(imp11);

			// MyLog.waitHere(listaMessaggi(19), debug);
			manual = true;
		}

		if (!manual) {
			// reimposto i punti trovati
			PointRoi pr12 = new PointRoi(xPoints3, yPoints3, xPoints3.length);
			pr12.setPointType(2);	
			pr12.setSize(4);		
			imp12.setRoi(pr12);
			
			if (demo) {
				
	//			imp12.updateAndDraw();
			
	// ridisegno i punti sull'overlay
				imp12.getRoi().setStrokeColor(colore1);			
				over12.addElement(imp12.getRoi());			
				imp12.setOverlay(over12);				
				imp12.updateAndDraw();
				MyLog.waitHere(listaMessaggi(15), debug, timeout);
		}
			// ---------------------------------------------------
			// eseguo ora fitCircle per trovare centro e dimensione del
			// fantoccio
			// ---------------------------------------------------
			ImageUtils.fitCircle(imp12);
			if (demo) {
				imp12.getRoi().setStrokeColor(colore3);
				over12.addElement(imp12.getRoi());
			}

			if (demo)
				MyLog.waitHere(listaMessaggi(16), debug, timeout);
			Rectangle boundRec = imp12.getProcessor().getRoi();
			xCenterCircle = Math.round(boundRec.x + boundRec.width / 2);
			yCenterCircle = Math.round(boundRec.y + boundRec.height / 2);
			diamCircle = boundRec.width;
			if (!manualOverride)
				writeStoredRoiData(boundRec);

			MyCircleDetector.drawCenter(imp12, over12, xCenterCircle, yCenterCircle, colore3);

			if (demo)
				MyLog.waitHere(listaMessaggi(17), debug, timeout);

			// ----------------------------------------------------------
			// Misuro l'errore sul fit rispetto ai punti imposti
			// -----------------------------------------------------------
			double[] vetDist = new double[xPoints3.length];
			double sumError = 0;
			for (int i1 = 0; i1 < xPoints3.length; i1++) {
				vetDist[i1] = ImageUtils.pointCirconferenceDistance(xPoints3[i1], yPoints3[i1], xCenterCircle,
						yCenterCircle, diamCircle / 2);
				sumError += Math.abs(vetDist[i1]);
			}
			if (sumError > maxFitError) {
				// MyLog.waitHere("maxFitError");
				// -------------------------------------------------------------
				// disegno il cerchio ed i punti, in modo da date un feedback
				// grafico al messaggio di eccessivo errore nel fit
				// -------------------------------------------------------------
				UtilAyv.showImageMaximized(imp12);
				over12.remove(pr12);
				imp12.setOverlay(over12);
				imp12.setRoi(new OvalRoi(xCenterCircle - diamCircle / 2, yCenterCircle - diamCircle / 2, diamCircle,
						diamCircle));
				imp12.getRoi().setStrokeColor(colore2);
				over12.addElement(imp12.getRoi());
				imp12.setRoi(new PointRoi(xPoints3, yPoints3, xPoints3.length));
				imp12.getRoi().setStrokeColor(colore2);
				over12.addElement(imp12.getRoi());
				imp12.deleteRoi();
				// MyLog.logVector(xPoints3, "xPoints3");
				// MyLog.logVector(yPoints3, "yPoints3");
				MyLog.waitHere(listaMessaggi(18) + " erano " + xPoints3.length + " punti", debug);
				manual = true;
			}

		}

		// MyLog.waitHere("manual= " + manual);
		// MyLog.waitHere("xPoints3.length= " + xPoints3.length);

		// ----------------------------------------------------------------------
		// Verifica di avere trovato almeno 3 punti, altrimenti chiede la
		// selezione manuale del cerchio
		// -------------------------------------------------------------------
		if (xPoints3 != null && xPoints3.length >= 3 && !manual) {
			// MyLog.waitHere("AUTO");
			imp12.setRoi(new PointRoi(xPoints3, yPoints3, xPoints3.length));
			ImageUtils.fitCircle(imp12);
			if (demo) {
				over12.addElement(imp12.getRoi());
				over12.setStrokeColor(Color.red);
			}

		} else {
			// NON SI SONO DETERMINATI 3 PUNTI DEL CERCHIO, SELEZIONE MANUALE
			Rectangle boundRec1 = null;
			Rectangle boundRec2 = null;

			if (!imp11.isVisible())
				UtilAyv.showImageMaximized(imp11);
			// UtilAyv.showImageMaximized(imp11);
			// ImageUtils.imageToFront(iw11);
			imp11.setRoi(new OvalRoi((width / 2) - 100, (height / 2) - 100, 200, 200));
			imp11.updateAndDraw();
			boundRec1 = imp11.getProcessor().getRoi();

			MyLog.waitHere(listaMessaggi(19), debug, timeout);

			// OBBLIGO A CAMBIARE QUALCOSA PER PREVENIRE L'OK "SCIMMIA"
			if (timeout > 0) {
				IJ.wait(100);
				imp11.setRoi(new OvalRoi((width / 2) - 101, (height / 2) - 100, 200, 200));
			}

			boundRec2 = imp11.getProcessor().getRoi();

			while (boundRec1.equals(boundRec2)) {
				MyLog.waitHere(listaMessaggi(40), debug);
				boundRec2 = imp11.getProcessor().getRoi();
			}

			//
			// Ho cos� risolto la mancata localizzazione automatica del
			// fantoccio (messaggi non visualizzati in junit)
			//
		}

		// ==========================================================================
		// ==========================================================================
		// porto in primo piano l'immagine originale
		ImageUtils.imageToFront(iw11);
		// ==========================================================================
		// ==========================================================================
		imp11.setOverlay(over12);

		Rectangle boundRec = null;
		if (manual)
			boundRec = imp11.getProcessor().getRoi();
		else
			boundRec = imp12.getProcessor().getRoi();

		imp12.close();
		// x1 ed y1 sono le due coordinate del centro

		xCenterCircle = boundRec.x + boundRec.width / 2;
		yCenterCircle = boundRec.y + boundRec.height / 2;
		diamCircle = boundRec.width;
		MyCircleDetector.drawCenter(imp11, over12, xCenterCircle, yCenterCircle, Color.red);

		// ----------------------------------------------------------
		// disegno la ROI del maxima, a solo scopo dimostrativo !
		// ----------------------------------------------------------
		//

		// x1 ed y1 sono le due coordinate del punto di maxima

		// double[] out10 = UtilAyv.findMaximumPosition(imp12);

		double[] out10 = MyFilter.maxPosition11x11(imp11);
		xMaxima = out10[0];
		yMaxima = out10[1];

		// over12.clear();

		if (demo) {
			MyCircleDetector.drawCenter(imp11, over12, (int) xMaxima, (int) yMaxima, Color.green);

			if (demo)
				MyLog.waitHere(listaMessaggi(20), debug, timeout);

		}
		imp12.killRoi();

		// ===============================================================
		// intersezioni retta - circonferenza
		// ===============================================================

		double[] out11 = ImageUtils.getCircleLineCrossingPoints(xCenterCircle, yCenterCircle, xMaxima, yMaxima,
				xCenterCircle, yCenterCircle, diamCircle / 2);

		// il punto che ci interesasa sar� quello con minor distanza dal
		// maxima
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

		if (demo)
			MyCircleDetector.drawCenter(imp11, over12, (int) xBordo, (int) yBordo, Color.pink);

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

		imp11.setRoi(new Line(xCenterCircle, yCenterCircle, (int) xBordo, (int) yBordo));
		angle11 = imp11.getRoi().getAngle(xCenterCircle, yCenterCircle, (int) xBordo, (int) yBordo);

		over12.addElement(imp11.getRoi());
		over12.setStrokeColor(Color.red);
		//
		// -----------------------------------------------------------
		// Calcolo coordinate centro della MROI
		// ----------------------------------------------------------
		//

		double[] out1 = interpolaProfondCentroROI(xEndRefLine, yEndRefLine, xStartRefLine, yStartRefLine,
				profond / dimPixel);
		ax = out1[0];
		ay = out1[1];

		if (demo) {
			MyCircleDetector.drawCenter(imp11, over12, (int) ax, (int) ay, Color.yellow);
			MyLog.waitHere(listaMessaggi(21), debug, timeout);
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

		if (demo && manual) {

			ImageUtils.imageToFront(iw11);

			// UtilAyv.showImageMaximized(imp11);
			imp11.setOverlay(over12);
			imp11.setRoi((int) ax - sqNEA / 2, (int) ay - sqNEA / 2, sqNEA, sqNEA);
			imp11.updateAndDraw();
			if (demo)
				MyLog.waitHere(info1 + "\n \nMODIFICA MANUALE POSIZIONE ROI", debug, timeout);
			//
			// Vado a rileggere solo le coordinate della ROI, quelle del
			// cerchio,
			// del punto di maxima e dell'angolo resteranno quelle determinate
			// in
			// precedenza (anche perch� non vengono comunque pi� utilizzate
			// per
			// i
			// calcoli)
			//
			Rectangle boundRec3 = imp11.getProcessor().getRoi();
			xCenterRoi = boundRec3.getCenterX();
			yCenterRoi = boundRec3.getCenterY();

		}

		double[] out2 = new double[10];
		out2[0] = xCenterRoi;
		out2[1] = yCenterRoi;
		out2[2] = xCenterCircle;
		out2[3] = yCenterCircle;

		out2[4] = xMaxima;
		out2[5] = yMaxima;
		out2[6] = angle11;
		out2[7] = xBordo;
		out2[8] = yBordo;
		out2[9] = diamCircle;
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

		String seriesDescription1 = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SERIES_DESCRIPTION);
		String seriesDescription2 = ReadDicom.readDicomParameter(imp2, MyConst.DICOM_SERIES_DESCRIPTION);
		if (seriesDescription1.equals(seriesDescription2))
			ok1 = true;
		String coil1 = ReadDicom.getAllCoils(imp1);
		String coil2 = ReadDicom.getAllCoils(imp2);
		if (coil1.equals(coil2))
			ok2 = true;
		return ok1 && ok2;
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
	public static double[][] cannyProfileAnalyzer(ImagePlus imp1, double dimPixel, String title, boolean showProfiles,
			boolean demo, boolean debug) {

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
		// devo ora contare i pixel a 255 che ho trovato, ne accetter� solo 2,
		if (count1 != 2) {
			if (demo)
				MyLog.waitHere("" + title + " trovati un numero di punti diverso da 2, count= " + count1
						+ " scartiamo questi risultati");
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
		// AGGIUNGO 1 AI PUNTI TROVATI
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
				MyLog.waitHere("Attenzione trovate troppe intersezioni col cerchio, cio� " + peaks1[2].length
						+ "  VERIFICARE");
			if (peaks1[2].length < 2)
				MyLog.waitHere("Attenzione trovata una sola intersezione col cerchio, cio� " + peaks1[2].length
						+ "  VERIFICARE");

			// MyLog.logMatrix(peaks1, "peaks1 " + title);
		}
		return peaks1;
	}

	/**
	 * Write preferences into IJ_Prefs.txt
	 * 
	 * @param boundingRectangle
	 */
	public static void writeStoredRoiData(Rectangle boundingRectangle) {

		Prefs.set("prefer.p10rmnDiamFantoc", Integer.toString(boundingRectangle.width));
		Prefs.set("prefer.p10rmnXRoi1", Integer.toString(boundingRectangle.x));
		Prefs.set("prefer.p10rmnYRoi1", Integer.toString(boundingRectangle.y));
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
	 * Qui sono raggruppati tutti i messaggi del plugin, in questo modo e'
	 * facilitata la eventuale modifica / traduzione (quando mai?) dei messaggi.
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
		lista[3] = "Eseguito VARIANCE";
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
		lista[15] = "Sono mostrati in ROSSO i punti utilizzati per il fit della circonferenza";
		lista[16] = "La circonferenza risultante dal fit e' mostrata in rosso";
		lista[17] = "Il centro del fantoccio e' contrassegnato dal pallino rosso";
		lista[18] = "<< SELEZIONE MANUALE >>\n " + "Troppa distanza tra i punti forniti ed il fit del cerchio";
		lista[19] = "<< SELEZIONE MANUALE >>\n"
				+ "Non si riescono a determinare le coordinate di almeno 3 punti del cerchio \n"
				+ "posizionare manualmente una ROI circolare di diametro uguale al fantoccio e\n" + "premere  OK";

		lista[20] = "Analizzando l'intera immagine con una ROI 11x11, viene determinata la \n"
				+ "posizione con la massima media nell'immagine, la posizione e' contrassegnata \n"
				+ "dal pallino verde";
		// ---------+-----------------------------------------------------------+
		lista[21] = "Lungo il segmento che unisce il pallino rosso del centro con il pallino verde \n"
				+ "del maximo ed il pallino rosa sulla circonferenza, viene calcolata la posizione \n"
				+ "del centro MROI, contrassegnato dal pallino giallo,  posto alla profondita' \n"
				+ "impostata nel file codiciNew.csv per il codice di questo controllo";
		lista[22] = "Accrescimento NEA riuscito, pixels validi= ";
		lista[23] = "mean4= ";
		lista[24] = "SNR finale= ";
		lista[25] = "displayNEA";
		lista[26] = "Viene definita una bkgROI sul fondo, evidenziata in giallo. \n" + "Segnale medio fondo= ";
		lista[27] = "Viene calcolata l'immagine differenza";
		lista[28] = "Profilo";
		lista[29] = "messaggio 29";
		// ---------+-----------------------------------------------------------+
		lista[30] = "Viene definita una Noise Estimate Area NEA 11x11 evidenziata in verde";
		lista[31] = "Viene definita una MROI 7x7 evidenziata in rosso";
		lista[32] = "ATTENZIONE la NEA esce dall'immagine senza riuscire a \n"
				+ "trovare 121 pixel che superino il  test il programma \n" + "TERMINA PREMATURAMENTE";
		lista[33] = "Disegnata Mroi su imaDiff";
		lista[34] = "Sulla MROI della ImaDiff otteniamo la stdDev = ";
		lista[35] = "Accrescimento MROI lato= ";
		lista[36] = "messaggio 36";
		lista[37] = "messaggio 37";
		lista[38] = "messaggio 38";
		lista[39] = "messaggio 39";
		lista[40] = "ATTENZIONE, l'intervento manuale OBBLIGATORIO deve cambiare qualcosa, anche minima, \n"
				+ "rispetto alla ROI proposta!!!";
		;

		// ---------+-----------------------------------------------------------+
		lista[65] = "vetMinimi==null, verificare esistenza della riga P10MIN nel file limiti.csv";
		lista[66] = "vetMaximi==null, verificare esistenza della riga P10MAX nel file limiti.csv";
		// ---------+-----------------------------------------------------------+

		String out = lista[select];
		return out;
	}


}