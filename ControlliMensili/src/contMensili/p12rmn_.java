package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.NewImage;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.Editor;
import ij.process.FloatPolygon;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import ij.util.Tools;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;

import org.w3c.dom.css.Rect;

import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyCircleDetector;
import utils.MyInput;
import utils.MyMsg;
import utils.MyConst;
import utils.MyFwhm;
import utils.MyLine;
import utils.MyLog;
import utils.MyPlot;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReadVersion;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableLimiti;
import utils.TableSequence;
import utils.UtilAyv;

/*
 * Copyright (C) 2007-2014 Alberto Duina, SPEDALI CIVILI DI BRESCIA, Brescia ITALY
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
 * AUTOMATIZZAZIONE del programma manuale esistente, p3 rmn che diventa p12rmn
 * 
 * Analizza UNIFORMITA', SNR per le bobine circolari vale per le immagini
 * Aggiunta al report anche la voce relativa al fondo: segnale medio e posizione
 * della roi. L'aggiunta è l'ultima voce del report, verrà pertanto
 * semplicementre ignorata dai vari autoreports e autohistory
 * 
 * +++++++++++++++++ MA A ME SEMBRA CHE FUNZIONI ++++++++++++++++++++++
 * +++++++++++++++++ MA A ME SEMBRA CHE FUNZIONI ++++++++++++++++++++++
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */
public class p12rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "UNIFORMITA' SNR GHOSTS per bobine circolari automatico";

	private String TYPE = " >> CONTROLLO UNIFORMITA' IMMAGINI CIRCOLARI AUTO";

	// private static String simulataName = "";

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.txt
	 */
	private static String fileDir = "";

	private static final boolean debug = true;
	// private static boolean junitTest = false;
	private static int timeout = 0;

	public void run(String args) {

		UtilAyv.setMyPrecision();

		Count c1 = new Count();
		if (!c1.jarCount("iw2ayv_"))
			return;

		String className = this.getClass().getName();

		VERSION = className + "_build_" + MyVersion.getVersion()
				+ "_iw2ayv_build_" + MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");

		if (IJ.versionLessThan("1.43k"))
			return;

		// IJ.run("Colors...",
		// "foreground=white background=black selection=yellow");

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}

		return;
	}

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
		boolean demo = false;
		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				new AboutBox().about("Controllo Uniformità",
						MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				timeout = MyInput.myIntegerInput(
						"Ritardo avanzamento (0 = infinito)", "      [msec]",
						1000, 0);
				selfTestMenu();
				retry = true;
				break;
			case 4:
				step = true;
				demo = true;
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
				boolean test = false;
				boolean silent = false;
				mainUnifor(path1, path2, "0", "", autoCalled, step, demo, test,
						fast, silent, timeout);

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
		MyLog.appendLog(fileDir + "MyLog.txt", "p12 riceve " + autoArgs);

		boolean fast = Prefs.get("prefer.fast", "false").equals("true") ? true
				: false;

		ResultsTable result1 = null;
		// IJ.log("p10rmn_.autoMenu fast= " + fast);
		// IJ.log("p10rmn_.autoMenu autoargs= " + autoArgs);
		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p12rmn_");
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

		if (nTokens == MyConst.TOKENS2) {
			UtilAyv.checkImages(vetRiga, iw2ayvTable, 2, debug);
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
			MyLog.logDebug(vetRiga[0], "P12", fileDir);
			MyLog.logDebug(vetRiga[1], "P12", fileDir);

		} else {
			UtilAyv.checkImages(vetRiga, iw2ayvTable, 3, debug);
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
			MyLog.logDebug(vetRiga[0], "P12", fileDir);
			MyLog.logDebug(vetRiga[1], "P12", fileDir);
		}

		boolean step = true;
		boolean retry = false;

		if (fast) {
			retry = false;
			boolean autoCalled = true;
			boolean demo = false;
			boolean test = false;
			boolean silent = false;

			result1 = mainUnifor(path1, path2, autoArgs, info10, autoCalled,
					step, demo, test, fast, silent, timeout);

			if (!(result1 == null))
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, result1);

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
					new AboutBox().about("Controllo Uniformità",
							MyVersion.CURRENT_VERSION);
					retry = true;
					break;
				case 3:
					step = true;
				case 4:
					retry = false;
					boolean autoCalled = true;
					boolean demo = step;
					boolean test = false;
					boolean silent = false;

					result1 = mainUnifor(path1, path2, autoArgs, info10,
							autoCalled, step, demo, test, fast, silent, timeout);
					if (result1 == null) {
						break;
					}

					UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, result1);

					UtilAyv.afterWork();
					break;
				}
			} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		if (result1 == null) {
			int resp = MyLog.waitHere("A causa di problemi sulla immagine, \n"
					+ "viene avviato il programma p3rmn_, che \n"
					+ "ripete il controllo in maniera manuale", debug,
					"Prosegui", "Annulla");
			if (resp == 2)
				IJ.runPlugIn("contMensili.p3rmn_", autoArgs);
		}

		return 0;
	}

	/**
	 * Main per il calcolo dell'uniformità per bobine circolari
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
	 * @param demo
	 *            flag true se verbose
	 * @param test
	 *            flag true se in modo test
	 * @param fast
	 *            flag true se in modo batch
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public static ResultsTable mainUnifor(String path1, String path2,
			String autoArgs, String info10, boolean autoCalled, boolean step,
			boolean demo, boolean test, boolean fast, boolean silent,
			int timeout) {

		boolean accetta = false;

		ResultsTable rt = null;
		Toolkit tk = Toolkit.getDefaultToolkit();
		ImageWindow iw1 = null;

		UtilAyv.setMeasure(MEAN + STD_DEV);
		// double angle = Double.NaN;
		boolean abort = false;
		// lettura dei limiti da file esterno
		boolean absolute = false;
		// String[][] limiti = new InputOutput().readFile6LIKE("LIMITI.csv",
		// absolute);

		String[][] limiti = TableLimiti.loadTable(MyConst.LIMITS_FILE);

		double[] vetMinimi = UtilAyv.doubleLimiti(UtilAyv.decoderLimiti(limiti,
				"P12MIN"));
		double[] vetMaximi = UtilAyv.doubleLimiti(UtilAyv.decoderLimiti(limiti,
				"P12MAX"));

		// ===============================================================
		// ATTENZIONE: questi sono solo valori di default utilizzati in
		// assenza di limiti.csv
		// DOMANDA: le misure di distanza sono in pixel o mm ?????
		// ================================================================
		double minMean1 = +10;
		double maxMean1 = +4096;
		double minNoiseImaDiff = -2048;
		double maxNoiseImaDiff = +2048;
		double minSnRatio = +10;
		double maxSnRatio = +800;
		double minGhostPerc = -20;
		double maxGhostPerc = +20;
		double minUiPerc = +5;
		double maxUiPerc = +100;
		double minFitError = +0;
		double maxFitError = +20;
		double minBubbleGapLimit = 0;
		double maxBubbleGapLimit = 2;

		// ================================================================
		if (vetMinimi == null) {
			MyLog.waitHere(listaMessaggi(65));
		} else {
			minMean1 = vetMinimi[0];
			minNoiseImaDiff = vetMinimi[1];
			minSnRatio = vetMinimi[2];
			minGhostPerc = vetMinimi[3];
			minUiPerc = vetMinimi[4];
			minFitError = vetMinimi[5];
			minBubbleGapLimit = vetMinimi[6];
		}
		if (vetMaximi == null) {
			MyLog.waitHere(listaMessaggi(65));
		} else {
			maxMean1 = vetMaximi[0];
			maxNoiseImaDiff = vetMaximi[1];
			maxSnRatio = vetMaximi[2];
			maxGhostPerc = vetMaximi[3];
			maxUiPerc = vetMaximi[4];
			maxFitError = vetMaximi[5];
			maxBubbleGapLimit = vetMaximi[6];
		}

		do {
			// ===============================================
			ImagePlus imp11 = null;
			if (demo) {
				imp11 = UtilAyv.openImageMaximized(path1);
				ImageUtils.imageToFront(imp11);
			} else
				imp11 = UtilAyv.openImageNoDisplay(path1, true);
			if (imp11 == null)
				MyLog.waitHere("Non trovato il file " + path1);
			ImagePlus imp13 = UtilAyv.openImageNoDisplay(path2, true);
			if (imp13 == null)
				MyLog.waitHere("Non trovato il file " + path2);

			double out2[] = positionSearch11(imp11, maxFitError,
					maxBubbleGapLimit, info10, autoCalled, step, demo, test,
					fast, timeout);
			if (out2 == null) {
				MyLog.waitHere("out2==null");
				return null;
			}

			Overlay over11 = new Overlay();
			Overlay over11b = new Overlay();
			over11.setStrokeColor(Color.red);
			imp11.setOverlay(over11);
			// ---------------------------------
			int xCenterCircle = (int) out2[0];
			int yCenterCircle = (int) out2[1];
			int diamCircle = (int) out2[2];
			int xCenter80 = (int) out2[3];
			int yCenter80 = (int) out2[4];
			int diam80 = (int) out2[5];
			// =========================================
			double[] circleData = out2;
			int diamGhost = 20;
			int guard = 10;
			double[][] out3 = p12rmn_.positionSearch13(imp11, circleData,
					diamGhost, guard, "", autoCalled, step, demo, test, fast,
					timeout);
			if (out3 == null) {
				MyLog.waitHere("out3==null");
				return null;
			}
			// ---------------------------------

			double xGhMaxDw = out3[0][0];
			double yGhMaxDw = out3[1][0];
			double xGhMaxSx = out3[0][1];
			double yGhMaxSx = out3[1][1];
			double xGhMaxDx = out3[0][2];
			double yGhMaxDx = out3[1][2];
			double xGhMaxUp = out3[0][3];
			double yGhMaxUp = out3[1][3];
			imp11.setRoi(new OvalRoi(xGhMaxDw - diamGhost / 2, yGhMaxDw
					- diamGhost / 2, diamGhost, diamGhost));
			if (demo) {
				imp11.getRoi().setStrokeColor(Color.green);
				over11.addElement(imp11.getRoi());
				over11b.addElement(imp11.getRoi());
				drawLabel(imp11, "dw");
				over11.addElement(imp11.getRoi());
			}
			imp11.setRoi(new OvalRoi(xGhMaxSx - diamGhost / 2, yGhMaxSx
					- diamGhost / 2, diamGhost, diamGhost));
			if (demo) {
				imp11.getRoi().setStrokeColor(Color.green);
				over11.addElement(imp11.getRoi());
				over11b.addElement(imp11.getRoi());
				drawLabel(imp11, "sx");
				over11.addElement(imp11.getRoi());
			}
			imp11.setRoi(new OvalRoi(xGhMaxDx - diamGhost / 2, yGhMaxDx
					- diamGhost / 2, diamGhost, diamGhost));
			if (demo) {
				imp11.getRoi().setStrokeColor(Color.green);
				over11.addElement(imp11.getRoi());
				over11b.addElement(imp11.getRoi());
				drawLabel(imp11, "dx");
				over11.addElement(imp11.getRoi());
			}

			imp11.setRoi(new OvalRoi(xGhMaxUp - diamGhost / 2, yGhMaxUp
					- diamGhost / 2, diamGhost, diamGhost));
			if (demo) {
				imp11.getRoi().setStrokeColor(Color.green);
				over11.addElement(imp11.getRoi());
				over11b.addElement(imp11.getRoi());
				drawLabel(imp11, "up");
				over11.addElement(imp11.getRoi());
			}

			// ================================================
			int diamFondo = 20;
			boolean irraggiungibile = false;
			int[] out4 = positionSearch14(imp11, circleData, diamFondo, guard,
					"", autoCalled, step, demo, test, fast, irraggiungibile,
					timeout);
			if (out4 == null) {
				MyLog.waitHere("out4==null");
				return null;
			}

			// ---------------------------------
			int xCenterFondo = out4[0];
			int yCenterFondo = out4[1];
			imp11.setRoi(new OvalRoi(xCenterFondo - diamFondo / 2, yCenterFondo
					- diamFondo / 2, diamFondo, diamFondo));
			imp11.getRoi().setStrokeColor(Color.green);
			over11.addElement(imp11.getRoi());
			over11b.addElement(imp11.getRoi());

			if (demo)
				drawLabel(imp11, "bkg");
			over11.addElement(imp11.getRoi());
			// ==========================================================
			if (demo)
				IJ.setMinAndMax(imp11, 10, 30);

			imp11.setOverlay(over11b);
			imp11.updateAndRepaintWindow();
			if (demo)
				MyLog.waitHere(listaMessaggi(35), debug, timeout);

			// In FAST arriviamo in questa posizione senza che niente appaia a
			// video se il posizionamento è andato a buon fine

			// ========================================================================
			// se non ho trovato la posizione mi ritrovo qui senza out validi,
			// in tal caso andrebbe attivato automaticamente, sulle
			// stesse immagini p3rmn_
			// ========================================================================
			ImagePlus imp1 = null;
			ImagePlus imp2 = null;
			if (demo) {
				imp1 = UtilAyv.openImageMaximized(path1);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			} else if (silent) {
				imp1 = UtilAyv.openImageNoDisplay(path1, true);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			} else {
				imp1 = UtilAyv.openImageMaximized(path1);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			}
			if (imp2 == null)
				MyLog.waitHere("Non trovato il file " + path2);
			// ============================================================================
			// Fine calcoli geometrici
			// Inizio calcoli Uniformità
			// ============================================================================

			// Recupero ora i dati di output da PositionSearch11
			// Overlay over2 = new Overlay();
			Overlay over1 = new Overlay(); // / con questo comando pulisco
											// l'overlay
			over1.setStrokeColor(Color.red);
			imp1.setOverlay(over1);

			// ---------------------------------
			// Visualizzo sull'immagine il posizionamento che verrà utilizzato
			// cerchio esterno fantoccio in rosso
			// ---------------------------------
			imp1.setRoi(new OvalRoi(xCenterCircle - diamCircle / 2,
					yCenterCircle - diamCircle / 2, diamCircle, diamCircle));
			imp1.getRoi().setStrokeColor(Color.red);
			over1.addElement(imp1.getRoi());
			// MyLog.waitHere("cerchio esterno rosso");

			int xRoi2 = (int) out2[3];
			int yRoi2 = (int) out2[4];
			int diamRoi2 = (int) out2[5];
			// ---------------------------------
			// Visualizzo sull'immagine il posizionamento che verrà utilizzato
			// MROI in verde
			// ---------------------------------
			imp1.setRoi(new OvalRoi(xRoi2 - diamRoi2 / 2, yRoi2 - diamRoi2 / 2,
					diamRoi2, diamRoi2));
			imp1.getRoi().setStrokeColor(Color.green);
			over1.addElement(imp1.getRoi());
			// MyLog.waitHere("MROI verde");

			ImageStatistics stat1 = imp1.getStatistics();
			double mean1 = stat1.mean;

			double uiPerc1 = uiPercCalculation(stat1.max, stat1.min);

			if (demo) {
				imp1.getRoi().setStrokeColor(Color.green);
				over1.addElement(imp1.getRoi());
				imp1.getRoi().setStrokeWidth(2);
				MyLog.waitHere(
						listaMessaggi(40)
								+ UtilAyv.printDoubleDecimals(mean1, 4), debug,
						timeout);
				MyLog.waitHere(
						listaMessaggi(46) + " massimo= "
								+ UtilAyv.printDoubleDecimals(stat1.max, 4)
								+ " minimo= "
								+ UtilAyv.printDoubleDecimals(stat1.min, 4),
						debug, timeout);
				MyLog.waitHere(
						listaMessaggi(47)
								+ UtilAyv.printDoubleDecimals(uiPerc1, 4),
						debug, timeout);
			}

			ImagePlus impDiff = UtilAyv.genImaDifference(imp1, imp2);
			Overlay overDiff = new Overlay();
			overDiff.setStrokeColor(Color.red);
			impDiff.setOverlay(overDiff);
			if (!silent && !fast)
				UtilAyv.showImageMaximized(impDiff);

			if (demo) {
				MyLog.waitHere(listaMessaggi(41), debug, timeout);
			}
			impDiff.setRoi(new OvalRoi(xRoi2 - diamRoi2 / 2, yRoi2 - diamRoi2
					/ 2, diamRoi2, diamRoi2));

			ImageStatistics statImaDiff = impDiff.getStatistics();

			// double meanImaDiff = statImaDiff.mean;
			double stdDevImaDiff = statImaDiff.stdDev;
			double noiseImaDiff = stdDevImaDiff / Math.sqrt(2);

			if (demo) {
				impDiff.getRoi().setStrokeColor(Color.red);
				impDiff.getRoi().setStrokeWidth(2);
				MyLog.waitHere(
						listaMessaggi(42) + stdDevImaDiff
								+ "\n \n  noise= SD / sqrt(2) \n \n  noise= "
								+ UtilAyv.printDoubleDecimals(noiseImaDiff, 4),
						debug, timeout);

			}

			double snRatio = Math.sqrt(2) * mean1 / stdDevImaDiff;
			if (demo) {
				MyLog.waitHere(
						listaMessaggi(43)
								+ UtilAyv.printDoubleDecimals(snRatio, 4),
						debug, timeout);
			}

			// calcolo dapprima il valore del fondo, questo mi servirà per
			// vedere se per caso ottengo dei valori di ghost estremamente
			// elevati, in questo caso richiederò la conferma manuale della
			// posizione trovata, poichè è possibile vi sia, ad esempio un altro
			// pezzo di fantoccio acquistito (tappo, fantoccio adiacente ecc.)

			int xRoi9 = xCenterFondo - MyConst.P12_DIAM_ROI_BACKGROUND / 2;
			int yRoi9 = yCenterFondo - MyConst.P12_DIAM_ROI_BACKGROUND / 2;

			ImageStatistics statBkg = ImageUtils.backCalc(xRoi9, yRoi9,
					MyConst.P12_DIAM_ROI_BACKGROUND, imp1, false, true, true);
			double mediaBkg = statBkg.mean;
			double devStBkg = statBkg.stdDev;

			if (demo)
				ImageUtils.autoAdjust(imp1, imp1.getProcessor());

			iw1 = imp1.getWindow();
			if (imp1.isVisible())
				ImageUtils.imageToFront(imp1);
			//
			// if (iw1 != null) {
			// WindowManager.setCurrentWindow(iw1);
			// WindowManager.setWindow(iw1);
			// }

			// ---------------------------------
			// Visualizzo sull'immagine il posizionamento che verrà utilizzato
			// per i GHOST in verde
			// ---------------------------------

			// iniziamo con il ghost inferiore
			imp1.setRoi(new OvalRoi(xGhMaxDw - diamGhost / 2, yGhMaxDw
					- diamGhost / 2, diamGhost, diamGhost));
			ImageStatistics statGh1 = imp1.getStatistics();
			double mediaGhost1 = statGh1.mean;
			Rectangle boundRec1 = null;
			double limitBkg = mediaBkg * 3 + 6 * devStBkg;
			if (mediaGhost1 > limitBkg) {
				MyLog.waitHere(listaMessaggi(24) + " mediaGhost= "
						+ mediaGhost1 + " limitBkg= " + limitBkg, debug);
				boundRec1 = imp1.getProcessor().getRoi();
				xGhMaxDw = boundRec1.x;
				yGhMaxDw = boundRec1.y;
				statGh1 = imp1.getStatistics();
			}
			imp1.getRoi().setStrokeColor(Color.green);
			if (demo)
				imp1.getRoi().setStrokeWidth(2);
			over1.addElement(imp1.getRoi());
			drawLabel(imp1, "dw");
			over1.addElement(imp1.getRoi());
			// MyLog.waitHere("ghost DW");

			// ghost sinistro (simpatico!)
			imp1.setRoi(new OvalRoi(xGhMaxSx - diamGhost / 2, yGhMaxSx
					- diamGhost / 2, diamGhost, diamGhost));
			ImageStatistics statGh2 = imp1.getStatistics();
			double mediaGhost2 = statGh2.mean;
			if (mediaGhost2 > limitBkg) {
				MyLog.waitHere(listaMessaggi(24) + " mediaGhost= "
						+ mediaGhost2 + " limitBkg= " + limitBkg, debug,
						timeout);
				boundRec1 = imp1.getProcessor().getRoi();
				xGhMaxSx = boundRec1.x;
				yGhMaxSx = boundRec1.y;
				statGh2 = imp1.getStatistics();
			}
			imp1.getRoi().setStrokeColor(Color.green);
			if (demo)
				imp1.getRoi().setStrokeWidth(2);
			over1.addElement(imp1.getRoi());
			drawLabel(imp1, "sx");
			over1.addElement(imp1.getRoi());
			// MyLog.waitHere("ghost SX");

			// ghost destro
			imp1.setRoi(new OvalRoi(xGhMaxDx - diamGhost / 2, yGhMaxDx
					- diamGhost / 2, diamGhost, diamGhost));
			ImageStatistics statGh3 = imp1.getStatistics();
			double mediaGhost3 = statGh3.mean;
			if (mediaGhost3 > limitBkg) {
				MyLog.waitHere(listaMessaggi(24) + " mediaGhost= "
						+ mediaGhost3 + " limitBkg= " + limitBkg, debug,
						timeout);
				boundRec1 = imp1.getProcessor().getRoi();
				xGhMaxDx = boundRec1.x;
				yGhMaxDx = boundRec1.y;
				statGh3 = imp1.getStatistics();
			}
			imp1.getRoi().setStrokeColor(Color.green);
			if (demo)
				imp1.getRoi().setStrokeWidth(2);
			over1.addElement(imp1.getRoi());
			drawLabel(imp1, "dx");
			over1.addElement(imp1.getRoi());
			// MyLog.waitHere("ghost DX");

			// ghost superiore
			imp1.setRoi(new OvalRoi(xGhMaxUp - diamGhost / 2, yGhMaxUp
					- diamGhost / 2, diamGhost, diamGhost));
			ImageStatistics statGh4 = imp1.getStatistics();
			double mediaGhost4 = statGh4.mean;
			if (mediaGhost4 > limitBkg) {
				MyLog.waitHere(listaMessaggi(24) + " mediaGhost= "
						+ mediaGhost4 + " limitBkg= " + limitBkg, debug,
						timeout);
				boundRec1 = imp1.getProcessor().getRoi();
				xGhMaxUp = boundRec1.x;
				yGhMaxUp = boundRec1.y;
				statGh3 = imp1.getStatistics();
			}
			imp1.getRoi().setStrokeColor(Color.green);
			if (demo)
				imp1.getRoi().setStrokeWidth(2);
			over1.addElement(imp1.getRoi());
			drawLabel(imp1, "up");
			over1.addElement(imp1.getRoi());
			// MyLog.waitHere("ghost UP");

			imp1.setRoi(new OvalRoi(xCenterFondo - diamFondo / 2, yCenterFondo
					- diamFondo / 2, diamFondo, diamFondo));
			imp1.getRoi().setStrokeColor(Color.orange);
			if (demo)
				imp1.getRoi().setStrokeWidth(2);
			over1.addElement(imp1.getRoi());
			drawLabel(imp1, "bkg");
			over1.addElement(imp1.getRoi());
			// MyLog.waitHere("BKG");

			imp1.deleteRoi();

			double ghostPerc1 = ghostPercCalculation(mediaGhost1, mediaBkg,
					mean1);
			double ghostPerc2 = ghostPercCalculation(mediaGhost2, mediaBkg,
					mean1);
			double ghostPerc3 = ghostPercCalculation(mediaGhost3, mediaBkg,
					mean1);
			double ghostPerc4 = ghostPercCalculation(mediaGhost4, mediaBkg,
					mean1);

			double slicePosition = ReadDicom.readDouble(ReadDicom
					.readDicomParameter(imp1, MyConst.DICOM_SLICE_LOCATION));

			if (demo) {
				ImageUtils.imageToFront(imp1);

				MyLog.waitHere(
						listaMessaggi(50) + "  ghostPerc1= "
								+ UtilAyv.printDoubleDecimals(ghostPerc1, 4)
								+ "\n" + "  ghostPerc2= "
								+ UtilAyv.printDoubleDecimals(ghostPerc2, 4)
								+ "\n" + "  ghostPerc3= "
								+ UtilAyv.printDoubleDecimals(ghostPerc3, 4)
								+ "\n" + "  ghostPerc4= "
								+ UtilAyv.printDoubleDecimals(ghostPerc4, 4),
						debug, timeout);
			}

			if (demo)
				MyLog.waitHere(listaMessaggi(44), debug, timeout);

			int[][] classiSimulata = generaSimulata(xCenter80 - diam80 / 2,
					yCenter80 - diam80 / 2, diam80, imp1, fileDir, step, demo,
					test);
			imp1.deleteRoi();

			String[][] tabCodici = TableCode
					.loadMultipleTable(MyConst.CODE_GROUP);
			if (demo)
				MyLog.waitHere(listaMessaggi(45), debug, timeout);
			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1,
					imp1, tabCodici, VERSION, autoCalled);

			if (iw1 != null) {
				WindowManager.setCurrentWindow(iw1);
				WindowManager.setWindow(iw1);
			}

			// =================================================================
			// Effettuo dei controlli "di sicurezza" sui valori calcolati,
			// in modo da evitare possibili sorprese
			// ================================================================

			if (!test) {
				if (UtilAyv.checkLimits2(mean1, minMean1, maxMean1, "mean1"))
					abort = true;
				if (UtilAyv.checkLimits2(noiseImaDiff, minNoiseImaDiff,
						maxNoiseImaDiff, "noiseImaDiff"))
					abort = true;
				if (UtilAyv.checkLimits2(snRatio, minSnRatio, maxSnRatio,
						"snRatio"))
					abort = true;
				if (UtilAyv.checkLimits2(ghostPerc1, minGhostPerc,
						maxGhostPerc, "ghostPerc1"))
					abort = true;
				if (UtilAyv.checkLimits2(ghostPerc2, minGhostPerc,
						maxGhostPerc, "ghostPerc2"))
					abort = true;
				if (UtilAyv.checkLimits2(ghostPerc3, minGhostPerc,
						maxGhostPerc, "ghostPerc3"))
					abort = true;
				if (UtilAyv.checkLimits2(ghostPerc4, minGhostPerc,
						maxGhostPerc, "ghostPerc4"))
					abort = true;
				if (UtilAyv.checkLimits2(uiPerc1, minUiPerc, maxUiPerc,
						"maxUiPerc"))
					abort = true;
				if (abort) {
					int resp = ButtonMessages
							.ModelessMsg(
									"Accettare il valore fuori range oppure rifare l'elaborazione in manuale",
									"ACCETTA", "MANUALE");
					if (resp == 1)
						return null;
				}
			}

			IJ.wait(MyConst.TEMPO_VISUALIZZ);
			// int col = 2;
			String t1 = "TESTO";
			String s2 = "VALORE";
			String s3 = "roi_x";
			String s4 = "roi_y";
			String s5 = "roi_b";
			String s6 = "roi_h";

			// put values in ResultsTable
			rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);

			rt.addLabel(t1, "Segnale");
			rt.addValue(s2, mean1);
			rt.addValue(s3, stat1.roiX);
			rt.addValue(s4, stat1.roiY);
			rt.addValue(s5, stat1.roiWidth);
			rt.addValue(s6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Rumore");
			rt.addValue(s2, noiseImaDiff);
			rt.addValue(s3, stat1.roiX);
			rt.addValue(s4, stat1.roiY);
			rt.addValue(s5, stat1.roiWidth);
			rt.addValue(s6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "SNR");
			rt.addValue(s2, snRatio);
			rt.addValue(s3, stat1.roiX);
			rt.addValue(s4, stat1.roiY);
			rt.addValue(s5, stat1.roiWidth);
			rt.addValue(s6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Ghost_1");
			rt.addValue(s2, ghostPerc1);
			rt.addValue(s3, statGh1.roiX);
			rt.addValue(s4, statGh1.roiY);
			rt.addValue(s5, statGh1.roiWidth);
			rt.addValue(s6, statGh1.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Ghost_2");
			rt.addValue(s2, ghostPerc2);
			rt.addValue(s3, statGh2.roiX);
			rt.addValue(s4, statGh2.roiY);
			rt.addValue(s5, statGh2.roiWidth);
			rt.addValue(s6, statGh2.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Ghost_3");
			rt.addValue(s2, ghostPerc3);
			rt.addValue(s3, statGh3.roiX);
			rt.addValue(s4, statGh3.roiY);
			rt.addValue(s5, statGh3.roiWidth);
			rt.addValue(s6, statGh3.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Ghost_4");
			rt.addValue(s2, ghostPerc4);
			rt.addValue(s3, statGh4.roiX);
			rt.addValue(s4, statGh4.roiY);
			rt.addValue(s5, statGh4.roiWidth);
			rt.addValue(s6, statGh4.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Unif.Integr.%");
			rt.addValue(s2, uiPerc1);
			rt.addValue(s3, stat1.roiX);
			rt.addValue(s4, stat1.roiY);
			rt.addValue(s5, stat1.roiWidth);
			rt.addValue(s6, stat1.roiHeight);

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

			String[] levelString = { "+20%", "+10%", "-10%", "-20%", "-30%",
					"-40%", "-50%", "-60%", "-70%", "-80%", "-90%", "fondo" };

			for (int i1 = 0; i1 < classiSimulata.length; i1++) {
				rt.incrementCounter();
				rt.addLabel(t1, ("Classe" + classiSimulata[i1][0]) + "_"
						+ levelString[i1]);
				rt.addValue(s2, classiSimulata[i1][1]);
			}

			if (demo && !test && !fast && !silent) {
				rt.show("Results");
			}

			if (fast || autoCalled || silent) {
				accetta = true;
			} else if (!test) {
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

	public boolean selfTestGe(boolean verbose) {
		boolean ok = selfTestSiemens(verbose);
		return ok;
	}

	public boolean selfTestSiemens(boolean verbose) {

		String home1 = findTestImages();
		String path1 = home1 + "/HUSA_001testP3";
		String path2 = home1 + "/HUSA_002testP3";

		double[] vetReference = referenceSiemens();

		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = false;
		boolean test = false;
		boolean fast = false;
		boolean silent = true;

		ResultsTable rt1 = mainUnifor(path1, path2, autoArgs, "", autoCalled,
				step, verbose, test, fast, silent, timeout);
		double[] vetResults = UtilAyv.vectorizeResults(rt1);
		boolean ok = UtilAyv.verifyResults1(vetResults, vetReference,
				MyConst.P3_vetName);
		if (verbose)
			UtilAyv.afterWork();

		return ok;
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
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY)
				.getPath();
		return (home1);
	}

	/**
	 * Siemens test image expected results
	 * 
	 * @return
	 */
	/**
	 * Siemens test image expected results
	 * 
	 * @return
	 */
	static double[] referenceSiemens() {

		double mean = 1680.5405176520405;
		double noise = 4.071338700948202;
		double snRatio = 412.77344900355456;
		double g5 = 0.1619430747878742;
		double g6 = -0.14066450798435123;
		double g7 = -0.13463871915503495;
		double g8 = 0.21542195064805592;
		double uiPerc = 91.53798641136504;
		double bkg = 10.80379746835443;
		double pos = 0.0;
		double c4 = 0.0;
		double c3 = 0.0;
		double c2 = 22558.0;
		double c1 = 379.0;
		double c0 = 42599.0;
		double[] vetReference = { mean, noise, snRatio, g5, g6, g7, g8, uiPerc,
				bkg, pos, c4, c3, c2, c1, c0 };
		return vetReference;
	}

	/**
	 * Automatic silent self test
	 */
	public void selfTestSilent() {
		boolean verbose = false;
		boolean ok = selfTestSiemens(verbose);
		if (ok) {
			IJ.log("Il test di p12rmn_ UNIFORMITA' SUPERFICIALE è stato SUPERATO");
		} else {
			IJ.log("Il test di p12rmn_ UNIFORMITA' SUPERFICIALE evidenzia degli ERRORI");
		}
		return;

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

	public static Double toDouble(double in) {
		Double out = new Double(in);
		return out;
	}

	/**
	 * Riceve una ImagePlus derivante da un CannyEdgeDetector con impostata una
	 * Line, restituisce le coordinate dei 2 picchi, se non sono esattamente 2
	 * restituisce null.
	 * 
	 * @param imp1
	 * @param dimPixel
	 * @param title
	 * @param showProfiles
	 * @param demo
	 * @param debug
	 * @return
	 */
	public static double[][] cannyProfileAnalyzer(ImagePlus imp1,
			double dimPixel, String title, boolean showProfiles, boolean demo,
			boolean debug, int timeout) {

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

		// peaks1 viene utilizzato in un altra routine, per cui gli elementi 0,
		// 1 e
		// ed 2 sono utilizzati per altro, li lascio a 0
		double[][] peaks1 = new double[6][count1];

		int count2 = 0;
		boolean ready2 = false;
		double max2 = 0;

		for (int i1 = 0; i1 < line1[0].length; i1++) {

			if (line1[2][i1] > max2) {
				peaks1[3][count2] = line1[0][i1];
				peaks1[4][count2] = line1[1][i1];
				max2 = line1[2][i1];
				peaks1[5][count2] = max2;

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
				MyLog.waitHere(listaMessaggi(3), debug, timeout);

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
		ArrayList<ArrayList<Double>> matOut = ImageUtils.peakDet2(profi3, 100.);
		double[][] peaks1 = new InputOutput()
				.fromArrayListToDoubleTable(matOut);

		double[] xPoints = new double[peaks1[0].length];
		double[] yPoints = new double[peaks1[0].length];
		for (int i1 = 0; i1 < peaks1[0].length; i1++) {
			xPoints[i1] = peaks1[0][i1];
			yPoints[i1] = peaks1[1][i1];

		}

		if (showProfiles) {
			Plot plot2 = MyPlot.basePlot(profi2x, profi2y, title, Color.GREEN);
			plot2.draw();
			plot2.setColor(Color.red);
			plot2.addPoints(xPoints, yPoints, PlotWindow.CIRCLE);
			plot2.show();
			new WaitForUserDialog("002 premere  OK").show();
		}

		// verifico di avere trovato un max di 2 picchi
		if (peaks1[2].length > 2)
			MyLog.waitHere("Attenzione trovati troppe intersezioni col cerchio, VERIFICARE");

		return peaks1;
	}

	/**
	 * Ricerca posizione ROI per calcolo uniformità. Versione con Canny Edge
	 * Detector
	 * 
	 * @param imp11
	 *            immagine in input
	 * @param info1
	 *            messaggio esplicativo
	 * @param autoCalled
	 *            true se chiamato in automatico
	 * @param step
	 *            true se in modo passo passo
	 * @param verbose
	 *            true se in modo verbose
	 * @param test
	 *            true se in test con junit, nessuna visualizzazione e richiesta
	 *            conferma
	 * @param fast
	 *            true se in modo batch
	 * @return
	 */
	public static double[] positionSearch11(ImagePlus imp11,
			double maxFitError, double maxBubbleGapLimit, String info1,
			boolean autoCalled, boolean step, boolean demo, boolean test,
			boolean fast, int timeout) {
		//
		// ================================================================================
		// Inizio calcoli geometrici
		// ================================================================================
		//
		boolean debug = false;
		boolean manual = false;

		int xCenterCircle = 0;
		int yCenterCircle = 0;
		int xCenterCircleMan = 0;
		int yCenterCircleMan = 0;
		int diamCircleMan = 0;
		int xCenterCircleMan80 = 0;
		int yCenterCircleMan80 = 0;
		int diamCircleMan80 = 0;
		int diamCircle = 0;
		int xCenterMROI = 0;
		int yCenterMROI = 0;
		int diamMROI = 0;
		int xcorr = 0;
		int ycorr = 0;

		int height = imp11.getHeight();
		int width = imp11.getWidth();
		int[] roiData = readPreferences(width, height, MyConst.P3_ROI_LIMIT);
		int diamRoiMan = roiData[2];

		ImageWindow iw11 = null;
		ImageWindow iw12 = null;
		if (demo) {
			iw11 = imp11.getWindow();
		}

		Overlay over12 = new Overlay();

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(
						ReadDicom.readDicomParameter(imp11,
								MyConst.DICOM_PIXEL_SPACING), 1));

		if (demo) {
			// UtilAyv.showImageMaximized(imp11);
			MyLog.waitHere(listaMessaggi(0), debug, timeout);
		}

		MyCannyEdgeDetector mce = new MyCannyEdgeDetector();
		mce.setGaussianKernelRadius(2.0f);
		mce.setLowThreshold(2.5f);
		mce.setHighThreshold(10.0f);
		mce.setContrastNormalized(false);

		ImagePlus imp12 = mce.process(imp11);
		imp12.setOverlay(over12);

		if (demo) {
			UtilAyv.showImageMaximized(imp12);
			MyLog.waitHere(listaMessaggi(1), debug, timeout);
			iw12 = imp12.getWindow();
		}

		double[][] peaks1 = new double[4][1];
		double[][] peaks2 = new double[4][1];
		double[][] peaks3 = new double[4][1];
		double[][] peaks4 = new double[4][1];
		double[][] peaks5 = new double[4][1];
		double[][] peaks6 = new double[4][1];
		double[][] peaks7 = new double[4][1];
		double[][] peaks8 = new double[4][1];
		double[][] peaks9 = new double[4][1];
		double[][] peaks10 = new double[4][1];
		double[][] peaks11 = new double[4][1];
		double[][] peaks12 = new double[4][1];

		boolean showProfiles = false;
		int[] xcoord = new int[2];
		int[] ycoord = new int[2];
		boolean strokewidth = true;
		double strWidth = 1.5;
		boolean manualOverride = false;

		// --------DIAGONALE SINISTRA---------------------
		xcoord[0] = 0;
		ycoord[0] = 0;
		xcoord[1] = width;
		ycoord[1] = height;
		imp12.setRoi(new Line(xcoord[0], ycoord[0], xcoord[1], ycoord[1]));
		if (demo) {
			// imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}
		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);
		peaks5 = cannyProfileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE SINISTRA", demo, demo, debug, timeout);
		if (peaks5 != null)
			ImageUtils.plotPoints(imp12, over12, peaks5);

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
		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);
		peaks6 = cannyProfileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE DESTRA", false, false, false, 1);
		if (peaks6 != null)
			ImageUtils.plotPoints(imp12, over12, peaks6);

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
		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);

		peaks1 = cannyProfileAnalyzer(imp12, dimPixel,
				"BISETTRICE ORIZZONTALE", showProfiles, false, false, 1);
		// PLOTTAGGIO PUNTI
		if (peaks1 != null)
			ImageUtils.plotPoints(imp12, over12, peaks1);

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

		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);
		peaks2 = cannyProfileAnalyzer(imp12, dimPixel, "BISETTRICE VERTICALE",
				showProfiles, false, false, 1);
		// PLOTTAGGIO PUNTI
		if (peaks2 != null)
			ImageUtils.plotPoints(imp12, over12, peaks2);

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
		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);
		peaks3 = cannyProfileAnalyzer(imp12, dimPixel, "BISETTRICE INCERTA SX",
				showProfiles, false, false, 1);
		// PLOTTAGGIO PUNTI
		if (peaks3 != null)
			ImageUtils.plotPoints(imp12, over12, peaks3);

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
		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);
		peaks4 = cannyProfileAnalyzer(imp12, dimPixel, "BISETTRICE INCERTA SX",
				showProfiles, false, false, 1);
		// PLOTTAGGIO PUNTI
		if (peaks4 != null)
			ImageUtils.plotPoints(imp12, over12, peaks4);
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
		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);
		peaks7 = cannyProfileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE DESTRA EXTRA", showProfiles, false,
				false, 1);
		// PLOTTAGGIO PUNTI
		if (peaks7 != null)
			ImageUtils.plotPoints(imp12, over12, peaks7);
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
		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);
		peaks8 = cannyProfileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE DESTRA EXTRA", showProfiles, false,
				false, 1);
		// PLOTTAGGIO PUNTI
		if (peaks8 != null)
			ImageUtils.plotPoints(imp12, over12, peaks8);
		// --------------------------------------------
		if (demo) {
			MyLog.waitHere(listaMessaggi(2), debug, timeout);
		}
		// --------------------------------------------
		int npeaks1 = 0;
		if (peaks1 != null) {
			npeaks1 = peaks1[2].length;
			if (npeaks1 > 1)
				npeaks1 = 1;
		}

		int npeaks2 = 0;
		if (peaks2 != null) {
			npeaks2 = peaks2[2].length;
			if (npeaks2 > 1)
				npeaks2 = 1;
		}
		int npeaks3 = 0;
		if (peaks3 != null)
			npeaks3 = peaks3[2].length;
		int npeaks4 = 0;
		if (peaks4 != null)
			npeaks4 = peaks4[2].length;
		int npeaks5 = 0;
		if (peaks5 != null)
			npeaks5 = peaks5[2].length;
		int npeaks6 = 0;
		if (peaks6 != null)
			npeaks6 = peaks6[2].length;
		int npeaks7 = 0;
		if (peaks7 != null)
			npeaks7 = peaks7[2].length;
		int npeaks8 = 0;
		if (peaks8 != null)
			npeaks8 = peaks8[2].length;
		int len3 = npeaks1 + npeaks2 + npeaks3 + npeaks4 + npeaks5 + npeaks6
				+ npeaks7 + npeaks8;
		int[] xPoints3 = new int[len3];
		int[] yPoints3 = new int[len3];
		int j1 = -1;

		if (demo)
			MyLog.waitHere(listaMessaggi(14), debug, timeout);
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
		over12.clear();

		// ----------------------------------------------------------------------
		// Verifica di avere trovato almeno 3 punti, altrimenti chiede la
		// selezione manuale del cerchio
		// -------------------------------------------------------------------

		if (xPoints3.length < 3 || test) {
			UtilAyv.showImageMaximized(imp11);
			MyLog.waitHere(listaMessaggi(17), debug, timeout);
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
				MyLog.waitHere(listaMessaggi(5), debug, timeout);
			}
			// ---------------------------------------------------
			// eseguo ora fitCircle per trovare centro e dimensione del
			// fantoccio
			// ---------------------------------------------------
			ImageUtils.fitCircle(imp12);
			if (demo) {
				imp12.getRoi().setStrokeColor(Color.red);
				over12.addElement(imp12.getRoi());
			}

			if (demo)
				MyLog.waitHere(listaMessaggi(6), debug, timeout);
			Rectangle boundRec = imp12.getProcessor().getRoi();
			xCenterCircle = Math.round(boundRec.x + boundRec.width / 2);
			yCenterCircle = Math.round(boundRec.y + boundRec.height / 2);
			diamCircle = boundRec.width;
			if (!manualOverride)
				writeStoredRoiData(boundRec);

			MyCircleDetector.drawCenter(imp12, over12, xCenterCircle,
					yCenterCircle, Color.red);

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
				MyLog.waitHere(listaMessaggi(16), debug, timeout);
				manual = true;
			}

			//
			// ----------------------------------------------------------
			// disegno la ROI del centro, a solo scopo dimostrativo !
			// ----------------------------------------------------------
			//

			if (demo) {
				MyCircleDetector.drawCenter(imp12, over12, xCenterCircle,
						yCenterCircle, Color.red);
				MyLog.waitHere(listaMessaggi(7), debug, timeout);

			}

			// =============================================================
			// COMPENSAZIONE PER EVENTUALE BOLLA D'ARIA NEL FANTOCCIO
			// ==============================================================

			// Traccio nuovamente le bisettrici verticale ed orizzontale, solo
			// che anzichè essere sul centro dell'immagine, ora sono poste sul
			// centro del cerchio circoscritto al fantoccio

			// BISETTRICE VERTICALE FANTOCCIO

			imp12.setRoi(new Line(xCenterCircle, 0, xCenterCircle, height));
			if (demo) {
				imp12.updateAndDraw();
				over12.addElement(imp12.getRoi());
			}
			peaks9 = cannyProfileAnalyzer(imp12, dimPixel,
					"BISETTRICE VERTICALE FANTOCCIO", showProfiles, false,
					false, 1);

			// MyLog.logMatrix(peaks9, "peaks9");
			// MyLog.waitHere();

			// PLOTTAGGIO PUNTI

			double gapVert = 0;
			if (peaks9 != null) {
				ImageUtils.plotPoints(imp12, over12, peaks9);
				gapVert = diamCircle / 2 - (yCenterCircle - peaks9[4][0]);
			}

			// BISETTRICE ORIZZONTALE FANTOCCIO

			imp12.setRoi(new Line(0, yCenterCircle, width, yCenterCircle));
			if (demo) {
				imp12.updateAndDraw();
				over12.addElement(imp12.getRoi());
			}
			peaks10 = cannyProfileAnalyzer(imp12, dimPixel,
					"BISETTRICE ORIZZONTALE FANTOCCIO", showProfiles, false,
					false, 1);

			double gapOrizz = 0;
			if (peaks10 != null) {
				ImageUtils.plotPoints(imp12, over12, peaks10);
				gapOrizz = diamCircle / 2 - (xCenterCircle - peaks10[3][0]);
			}

			if (demo)
				MyLog.waitHere(listaMessaggi(8) + maxBubbleGapLimit, debug,
						timeout);

			// Effettuo in ogni caso la correzione, solo che in assenza di bolla
			// d'aria la correzione sarà irrisoria, in presenza di bolla la
			// correzione sarà apprezzabile

			if (gapOrizz > gapVert) {
				xcorr = (int) gapOrizz / 2;
			} else {
				ycorr = (int) gapVert / 2;
			}

			// ---------------------------------------
			// qesto è il risultato della nostra correzione e saranno i dati
			// della MROI
			diamMROI = (int) Math.round(diamCircle
					* MyConst.P3_AREA_PERC_80_DIAM);

			xCenterMROI = xCenterCircle + xcorr;
			yCenterMROI = yCenterCircle + ycorr;
			// ---------------------------------------
			// verifico ora che l'entità della bolla non sia così grande da
			// portare l'area MROI troppo a contatto del profilo fantoccio
			// calcolato sulle bisettrici
			// ---------------------------------------
			imp12.setRoi(new Line(xCenterMROI, 0, xCenterMROI, height));
			if (demo) {
				imp12.updateAndDraw();
				over12.addElement(imp12.getRoi());
			}
			peaks11 = cannyProfileAnalyzer(imp12, dimPixel,
					"BISETTRICE VERTICALE MROI", showProfiles, false, false, 1);
			if (peaks11 != null) {
				// PLOTTAGGIO PUNTI
				ImageUtils.plotPoints(imp12, over12, peaks11);
			}

			imp12.setRoi(new Line(0, yCenterMROI, width, yCenterMROI));
			if (demo) {
				imp12.updateAndDraw();
				over12.addElement(imp12.getRoi());
			}
			peaks12 = cannyProfileAnalyzer(imp12, dimPixel,
					"BISETTRICE ORIZZONTALE MROI", showProfiles, false, false,
					1);
			if (peaks12 != null) {
				// PLOTTAGGIO PUNTI
				ImageUtils.plotPoints(imp12, over12, peaks12);
			}

			double d1 = maxBubbleGapLimit;
			double d2 = maxBubbleGapLimit;
			double d3 = maxBubbleGapLimit;
			double d4 = maxBubbleGapLimit;
			double dMin = 9999;

			// verticale
			if (peaks11 != null) {
				d1 = -(peaks11[4][0] - (yCenterMROI - diamMROI / 2));
				d2 = peaks11[4][1] - (yCenterMROI + diamMROI / 2);
			}
			// orizzontale
			if (peaks12 != null) {
				d3 = -(peaks12[3][0] - (xCenterMROI - diamMROI / 2));
				d4 = peaks12[3][1] - (xCenterMROI + diamMROI / 2);
			}

			dMin = Math.min(dMin, d1);
			dMin = Math.min(dMin, d2);
			dMin = Math.min(dMin, d3);
			dMin = Math.min(dMin, d4);

			if (dMin < maxBubbleGapLimit) {
				manual = true;
				// -------------------------------------------------------------
				// disegno il cerchio ed i punti, in modo da date un feedback
				// grafico al messaggio di eccessivo errore da bolla d'aria
				// -------------------------------------------------------------
				// UtilAyv.showImageMaximized(imp11);
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
				imp11.setRoi(new OvalRoi(xCenterMROI - diamMROI / 2,
						yCenterMROI - diamMROI / 2, diamMROI, diamMROI));
				imp11.getRoi().setStrokeColor(Color.red);
				over12.addElement(imp11.getRoi());
				imp11.setRoi(new PointRoi(xPoints3, yPoints3, xPoints3.length));
				imp11.getRoi().setStrokeColor(Color.green);
				over12.addElement(imp11.getRoi());
				imp11.deleteRoi();

				MyLog.waitHere(listaMessaggi(51) + " dMin= " + dMin, debug);
			} else {
				imp12.setRoi(new OvalRoi(xCenterMROI - diamMROI / 2,
						yCenterMROI - diamMROI / 2, diamMROI, diamMROI));
				Rectangle boundingRectangle2 = imp12.getProcessor().getRoi();
				diamMROI = (int) boundingRectangle2.width;
				xCenterMROI = boundingRectangle2.x + boundingRectangle2.width
						/ 2;
				yCenterMROI = boundingRectangle2.y + boundingRectangle2.height
						/ 2;
				// imp12.killRoi();
			}
		}

		if (manual) {
			// ==================================================================
			// INTERVENTO MANUALE PER CASI DISPERATI, SAN GENNARO PENSACI TU
			// ==================================================================
			fast = false;
			imp12.close();
			over12.clear();
			if (!imp11.isVisible())
				UtilAyv.showImageMaximized(imp11);
			imp11.setRoi(new OvalRoi(width / 2 - diamRoiMan / 2, height / 2
					- diamRoiMan / 2, diamRoiMan, diamRoiMan));
			MyLog.waitHere(listaMessaggi(14), debug, timeout);

			if (timeout > 0) {
				// in questo caso (simulazione in corso) devo simulare
				// l'intervento manuale dell'operatore. Lo simulo impostando una
				// nuova posizione della Roi con Roi.setLocation
				imp11.getRoi().setLocation(40, 29);
				imp11.updateAndDraw();
				// MyLog.waitHere();
			}

			Rectangle boundRec11 = imp11.getProcessor().getRoi();
			xCenterCircleMan = Math.round(boundRec11.x + boundRec11.width / 2);
			yCenterCircleMan = Math.round(boundRec11.y + boundRec11.height / 2);
			diamCircleMan = boundRec11.width;
			diamCircleMan80 = (int) Math.round(diamCircleMan
					* MyConst.P3_AREA_PERC_80_DIAM);
			imp11.setRoi(new OvalRoi(xCenterCircleMan - diamCircleMan80 / 2,
					yCenterCircleMan - diamCircleMan80 / 2, diamCircleMan80,
					diamCircleMan80));
			MyLog.waitHere(listaMessaggi(18), debug, timeout);

			Rectangle boundRec111 = imp11.getProcessor().getRoi();
			xCenterCircleMan80 = Math.round(boundRec111.x + boundRec111.width
					/ 2);
			yCenterCircleMan80 = Math.round(boundRec111.y + boundRec111.height
					/ 2);
			diamCircleMan80 = boundRec111.width;

			// carico qui i dati dell'avvenuto posizionamento manuale
			xCenterCircle = xCenterCircleMan;
			yCenterCircle = yCenterCircleMan;
			diamCircle = diamCircleMan;

			xCenterMROI = xCenterCircleMan80;
			yCenterMROI = yCenterCircleMan80;
			diamMROI = diamCircleMan80;
			imp12.setRoi(new OvalRoi(xCenterMROI, yCenterMROI, diamMROI,
					diamMROI));
		}

		if (demo) {
			imp12.updateAndDraw();
			imp12.getRoi().setStrokeColor(Color.green);
			over12.addElement(imp12.getRoi());
			MyLog.waitHere(listaMessaggi(9), debug, timeout);
			MyCircleDetector.drawCenter(imp12, over12, xCenterCircle + xcorr,
					yCenterCircle + ycorr, Color.green);
			MyLog.waitHere(listaMessaggi(10), debug, timeout);
		}

		imp12.close();
		double[] out2 = new double[6];
		out2[0] = xCenterCircle;
		out2[1] = yCenterCircle;
		out2[2] = diamCircle;
		out2[3] = xCenterMROI;
		out2[4] = yCenterMROI;
		out2[5] = diamMROI;
		return out2;
	}

	/***
	 * Posizionamento automatico delle roi su cui calcoleremo i ghosts
	 * 
	 */

	public static double[][] positionSearch13(ImagePlus imp1,
			double[] circleData, int diamGhost, int guard, String info1,
			boolean autoCalled, boolean step, boolean demo, boolean test,
			boolean fast, int timeout) {

		// leggo i dati del cerchio "esterno" del fantoccio e li plotto
		// sull'immagine

		boolean demo1 = demo;
		imp1.deleteRoi();

		ImagePlus imp2 = imp1.duplicate();
		if (demo)
			UtilAyv.showImageMaximized(imp2);

		Overlay over2 = new Overlay();
		imp2.setOverlay(over2);

		// IJ.run(imp2, "Set Scale...",
		// "distance=0 known=0 pixel=1 unit=pixel");

		int width = imp1.getWidth();
		int height = imp1.getHeight();

		int xCenterCircle = (int) circleData[0];
		int yCenterCircle = (int) circleData[1];
		int diamCircle = (int) circleData[2];
		// MyLog.waitHere("xCenterCircle= " + xCenterCircle + " yCenterCircle= "
		// + yCenterCircle + " diamCircle=" + diamCircle);

		// disegno il perimetro del fantoccio
		int xRoi0 = xCenterCircle - diamCircle / 2;
		int yRoi0 = yCenterCircle - diamCircle / 2;
		int diamRoi0 = diamCircle;

		imp2.setRoi(new OvalRoi(xRoi0, yRoi0, diamRoi0, diamRoi0));
		imp2.getRoi().setStrokeColor(Color.red);
		over2.addElement(imp2.getRoi());

		if (demo) {
			MyLog.waitHere(listaMessaggi(20), debug, timeout);
		}

		// marco con un punto il centro del fantoccio

		if (demo)
			MyCircleDetector.drawCenter(imp2, over2, xCenterCircle,
					yCenterCircle, Color.red);

		if (demo)
			IJ.setMinAndMax(imp2, 10, 100);

		// ghost di sinistra

		int critic_0;
		double ghMaxSx = -99999;
		int xGhMaxSx = -9999;
		int yGhMaxSx = -9999;

		for (int i1 = 0; i1 < diamCircle - diamGhost; i1 += 2) {
			for (int i2 = 0; i2 < diamCircle - diamGhost; i2 += 2) {
				int px = i2;
				int py = i1 + (yCenterCircle - diamCircle / 2);
				int xcentGhost = px + diamGhost / 2;
				int ycentGhost = py + diamGhost / 2;
				critic_0 = UtilAyv.criticalDistanceCalculation(xcentGhost,
						ycentGhost, diamGhost / 2, xCenterCircle,
						yCenterCircle, diamCircle / 2);
				if (critic_0 < guard) {
					break;
				}
				imp2.setRoi(new OvalRoi(px, py, diamGhost, diamGhost));
				double ghMean = imp2.getStatistics().mean;
				if (ghMean > ghMaxSx) {
					ghMaxSx = ghMean;
					xGhMaxSx = xcentGhost;
					yGhMaxSx = ycentGhost;
				}
				if (demo1) {
					over2.addElement(imp2.getRoi());
				}
			}
		}
		if (demo) {
			imp2.setRoi(new OvalRoi(xGhMaxSx - diamGhost / 2, yGhMaxSx
					- diamGhost / 2, diamGhost, diamGhost));
			imp2.getRoi().setStrokeColor(Color.red);
			over2.addElement(imp2.getRoi());
		}

		// ghost superiore
		int critic_1;
		double ghMaxUp = -99999;
		int xGhMaxUp = -9999;
		int yGhMaxUp = -9999;

		for (int i1 = 0; i1 < diamCircle - diamGhost; i1 += 2) {
			for (int i2 = 0; i2 < diamCircle - diamGhost; i2 += 2) {
				int py = i2;
				int px = i1 + (xCenterCircle - diamCircle / 2);
				int xcentGhost = px + diamGhost / 2;
				int ycentGhost = py + diamGhost / 2;
				critic_1 = UtilAyv.criticalDistanceCalculation(xcentGhost,
						ycentGhost, diamGhost / 2, xCenterCircle,
						yCenterCircle, diamCircle / 2);
				if (critic_1 < guard) {
					break;
				}
				imp2.setRoi(new OvalRoi(px, py, diamGhost, diamGhost));
				double ghMean = imp2.getStatistics().mean;
				if (ghMean > ghMaxUp) {
					ghMaxUp = ghMean;
					xGhMaxUp = xcentGhost;
					yGhMaxUp = ycentGhost;
				}
				if (demo1) {
					over2.addElement(imp2.getRoi());
				}
			}
		}
		if (demo) {
			imp2.setRoi(new OvalRoi(xGhMaxUp - diamGhost / 2, yGhMaxUp
					- diamGhost / 2, diamGhost, diamGhost));
			imp2.getRoi().setStrokeColor(Color.red);
			over2.addElement(imp2.getRoi());
		}

		// ghost di destra (e te pareva che non la buttavano in politica!)

		int critic_2;
		double ghMaxDx = -99999;
		int xGhMaxDx = -9999;
		int yGhMaxDx = -9999;

		for (int i2 = 0; i2 < diamCircle - diamGhost; i2 += 2) {
			for (int i1 = width - diamGhost; i1 > diamCircle - diamGhost; i1 -= 2) {
				int px = i1;
				int py = i2 + (yCenterCircle - diamCircle / 2);
				int xcentGhost = px + diamGhost / 2;
				int ycentGhost = py + diamGhost / 2;
				critic_2 = UtilAyv.criticalDistanceCalculation(xcentGhost,
						ycentGhost, diamGhost / 2, xCenterCircle,
						yCenterCircle, diamCircle / 2);
				if (critic_2 < guard) {
					break;
				}
				imp2.setRoi(new OvalRoi(px, py, diamGhost, diamGhost));
				double ghMean = imp2.getStatistics().mean;
				if (ghMean > ghMaxDx) {
					ghMaxDx = ghMean;
					xGhMaxDx = xcentGhost;
					yGhMaxDx = ycentGhost;
				}
				if (demo1) {
					over2.addElement(imp2.getRoi());
				}
			}
		}

		if (demo) {
			imp2.setRoi(new OvalRoi(xGhMaxDx - diamGhost / 2, yGhMaxDx
					- diamGhost / 2, diamGhost, diamGhost));
			imp2.getRoi().setStrokeColor(Color.red);
			over2.addElement(imp2.getRoi());
		}
		// IJ.log("ghost inferiore");

		int critic_3;
		double ghMaxDw = -99999;
		int xGhMaxDw = -9999;
		int yGhMaxDw = -9999;

		for (int i1 = 0; i1 < diamCircle - diamGhost; i1 += 2) {
			for (int i2 = height - diamGhost; i2 > diamCircle - diamGhost; i2 -= 2) {
				int py = i2;
				int px = i1 + (xCenterCircle - diamCircle / 2);
				int xcentGhost = px + diamGhost / 2;
				int ycentGhost = py + diamGhost / 2;
				critic_3 = UtilAyv.criticalDistanceCalculation(xcentGhost,
						ycentGhost, diamGhost / 2, xCenterCircle,
						yCenterCircle, diamCircle / 2);
				if (critic_3 < guard) {
					break;
				}
				imp2.setRoi(new OvalRoi(px, py, diamGhost, diamGhost));
				double ghMean = imp2.getStatistics().mean;
				if (ghMean > ghMaxDw) {
					ghMaxDw = ghMean;
					xGhMaxDw = xcentGhost;
					yGhMaxDw = ycentGhost;
				}
				if (demo1) {
					over2.addElement(imp2.getRoi());
				}
			}
		}

		if (demo) {
			imp2.setRoi(new OvalRoi(xGhMaxDw - diamGhost / 2, yGhMaxDw
					- diamGhost / 2, diamGhost, diamGhost));
			imp2.getRoi().setStrokeColor(Color.red);
			imp2.getRoi().setStrokeWidth(1.5);
			over2.addElement(imp2.getRoi());
			drawLabel(imp2, "dw");
			over2.addElement(imp2.getRoi());

			imp2.setRoi(new OvalRoi(xGhMaxSx - diamGhost / 2, yGhMaxSx
					- diamGhost / 2, diamGhost, diamGhost));
			imp2.getRoi().setStrokeColor(Color.red);
			imp2.getRoi().setStrokeWidth(1.5);
			over2.addElement(imp2.getRoi());
			drawLabel(imp2, "sx");
			over2.addElement(imp2.getRoi());

			imp2.setRoi(new OvalRoi(xGhMaxDx - diamGhost / 2, yGhMaxDx
					- diamGhost / 2, diamGhost, diamGhost));
			imp2.getRoi().setStrokeColor(Color.red);
			imp2.getRoi().setStrokeWidth(1.5);
			over2.addElement(imp2.getRoi());
			drawLabel(imp2, "dx");
			over2.addElement(imp2.getRoi());

			imp2.setRoi(new OvalRoi(xGhMaxUp - diamGhost / 2, yGhMaxUp
					- diamGhost / 2, diamGhost, diamGhost));
			imp2.getRoi().setStrokeColor(Color.red);
			imp2.getRoi().setStrokeWidth(1.5);
			over2.addElement(imp2.getRoi());
			drawLabel(imp2, "up");
			over2.addElement(imp2.getRoi());

			MyLog.waitHere(listaMessaggi(23), debug, timeout);
		}

		imp2.close();

		double[][] out1 = new double[2][4];
		out1[0][0] = xGhMaxDw;
		out1[1][0] = yGhMaxDw;
		out1[0][1] = xGhMaxSx;
		out1[1][1] = yGhMaxSx;
		out1[0][2] = xGhMaxDx;
		out1[1][2] = yGhMaxDx;
		out1[0][3] = xGhMaxUp;
		out1[1][3] = yGhMaxUp;

		return out1;
	}

	public static int[] positionSearch14(ImagePlus imp1, double[] circleData,
			int diamGhost, int guard, String info1, boolean autoCalled,
			boolean step, boolean demo, boolean test, boolean fast,
			boolean irraggiungibile, int timeout) {

		// leggo i dati del cerchio "esterno" del fantoccio e li plotto
		// sull'immagine
		boolean debug = true;

		Overlay over2 = new Overlay();
		over2.setStrokeColor(Color.red);
		imp1.deleteRoi();

		ImagePlus imp2 = imp1.duplicate();
		imp2.setOverlay(over2);
		if (demo) {
			IJ.setMinAndMax(imp2, 10, 50);
		}

		// IJ.run(imp2, "Set Scale...",
		// "distance=0 known=0 pixel=1 unit=pixel");

		int width = imp1.getWidth();
		int height = imp1.getHeight();

		int xCenterCircle = (int) circleData[0];
		int yCenterCircle = (int) circleData[1];
		int diamCircle = (int) circleData[2];

		// disegno il perimetro del fantoccio
		int xRoi0 = xCenterCircle - diamCircle / 2;
		int yRoi0 = yCenterCircle - diamCircle / 2;
		int diamRoi0 = diamCircle;

		ImageWindow iw2 = null;
		if (demo) {
			UtilAyv.showImageMaximized(imp2);
			iw2 = imp2.getWindow();
			// MyLog.waitHere(listaMessaggi(30), debug);
		}

		imp2.setRoi(new OvalRoi(xRoi0, yRoi0, diamRoi0, diamRoi0));
		if (demo) {
			imp2.getRoi().setStrokeColor(Color.red);
			over2.addElement(imp2.getRoi());
			MyLog.waitHere(listaMessaggi(30), debug, timeout);
		}

		// marco con un punto il centro del fantoccio

		int a = 0;
		if (irraggiungibile)
			a = 1;
		int critic_0;
		int px = 0;
		int py = 0;
		int incr = 1;
		boolean pieno = false;
		int xcentGhost = 0;
		int ycentGhost = 0;

		do {
			incr--;
			px = width - diamGhost + incr;
			py = height - diamGhost + incr;
			xcentGhost = px + diamGhost / 2;
			ycentGhost = py + diamGhost / 2;
			critic_0 = UtilAyv.criticalDistanceCalculation(xcentGhost,
					ycentGhost, diamGhost / 2, xCenterCircle, yCenterCircle,
					diamCircle / 2);
			imp2.setRoi(new OvalRoi(px, py, diamGhost, diamGhost));

			pieno = verifyCircularRoiPixels(imp2, xcentGhost, ycentGhost,
					diamGhost, test, demo);

			if (critic_0 < guard) {
				MyLog.waitHere("Ricerca posizione fondo fallita");
				return null;
			}
		} while (pieno || a > 0);
		if (demo) {
			MyLog.waitHere(listaMessaggi(33), debug, timeout);
		}

		imp2.close();

		int[] out1 = new int[3];
		out1[0] = xcentGhost;
		out1[1] = ycentGhost;
		out1[2] = diamGhost;

		return out1;
	}

	/***
	 * Cerco se all'interno del cerchio esiste almeno un area di 3x3 pixel a
	 * zero
	 * 
	 * @param imp1
	 * @param xRoi
	 * @param yRoi
	 * @param diamRoi
	 */
	public static void circleBlackAreaSearch(ImagePlus imp1, int xRoi,
			int yRoi, int diamRoi) {
		// disegno la Roi di diametro diamRoi (forse la diminuirò di 2 pixel)
		int xRoi0 = xRoi - diamRoi / 2;
		int yRoi0 = yRoi - diamRoi / 2;
		int diamRoi0 = diamRoi;
		// ImageProcessor ip1 = imp1.getProcessor();

		imp1.setRoi(new OvalRoi(xRoi0, yRoi0, diamRoi0, diamRoi0));
		// Rectangle r1 = ip1.getRoi();
		// tento un primo approccio, senza utilizzare la mask
		Roi roi1 = imp1.getRoi();
		Rectangle rect = roi1.getBounds();
		int rx = rect.x;
		int ry = rect.y;
		int w = rect.width;
		int h = rect.height;
		for (int y = ry; y < ry + h; y++) {
			for (int x = rx; x < rx + w; x++) {
				if (roi1.contains(x, y)) {

				}

			}
		}
	}

	/**
	 * Verifica che nella roi (beh, all'incirca) non sia presente un gruppo di
	 * 3x3 pixels. Utilizzata per verificare che nella posizione in cui si
	 * misura il segnale di fondo, non esistano spazi senza segnale (vedi
	 * immagini di Esine)
	 * 
	 * @param imp1
	 * @param xRoi
	 * @param yRoi
	 * @param diamRoi
	 * @return
	 */
	public static boolean verifyCircularRoiPixels(ImagePlus imp1, int xRoi,
			int yRoi, int diamRoi, boolean test, boolean demo) {
		int xRoi0 = xRoi - diamRoi / 2;
		int yRoi0 = yRoi - diamRoi / 2;
		int diamRoi0 = diamRoi;
		// effettua un adjust per esaltare il segnale di fondo
		Overlay over2 = new Overlay();

		if (demo) {
			over2.setStrokeColor(Color.red);
			imp1.setOverlay(over2);
		}

		imp1.setRoi(new OvalRoi(xRoi0, yRoi0, diamRoi0, diamRoi0));
		imp1.getRoi().setStrokeColor(Color.red);

		if (demo) {
			over2.addElement(imp1.getRoi());
			// IJ.setMinAndMax(imp1, 10, 30);
			// UtilAyv.showImageMaximized2(imp1);
		}
		ImageProcessor ip1 = imp1.getProcessor();
		Roi roi1 = imp1.getRoi();

		ImageProcessor mask = roi1.getMask();
		if (mask == null)
			MyLog.waitHere("mask==null");
		// prevengo problemi con le immagini calibrate
		short[] pixels = UtilAyv.truePixels(imp1);

		Rectangle roi2 = ip1.getRoi();
		int x1 = roi2.x;
		int y1 = roi2.y;
		int width = roi2.width;
		int height = roi2.height;
		int offset = 0;
		int[] vet = new int[9];
		// float[][] result = new float[width][height];
		ip1.setRoi(imp1.getRoi());
		byte[] maskArray = ip1.getMaskArray();
		if (maskArray == null)
			MyLog.waitHere("maskArray==null");
		int width1 = imp1.getWidth();
		int sum = 0;

		for (int i1 = 0; i1 < width; i1++) {
			for (int i2 = 0; i2 < height; i2++) {
				sum = 0;
				offset = (y1 + i2) * width1 + (x1 + i1);
				if (maskArray[i1 * width + i2] != 0) {
					// se questo pixel fa parte della roi, allora analizzo
					// l'intorno di 3x3 pixel
					vet[0] = offset - width1 - 1;
					vet[1] = offset - width1;
					vet[2] = offset - width1 + 1;
					vet[3] = offset - 1;
					vet[4] = offset;
					vet[5] = offset + 1;
					vet[6] = offset + width - 1;
					vet[7] = offset + width;
					vet[8] = offset + width + 1;
					for (int i4 = 0; i4 < 9; i4++) {
						if (vet[i4] <= pixels.length) {
							sum = sum + pixels[vet[i4]];
							if (demo) {
								imp1.setRoi(x1 + i1, y1 + i2, 1, 1);
								over2.addElement(imp1.getRoi());
							}
						}
					}
					if (sum == 0)
						return true;
				}
			}
		}
		return false;
	}

	/**
	 */

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

	// /**
	// * Lettura di un valore double da una stringa
	// *
	// * @param s1
	// * stringa di input
	// * @return double di output
	// */
	// public static double readDouble(String s1) {
	// double x = 0;
	// try {
	// x = (new Double(s1)).doubleValue();
	// } catch (Exception e) {
	// // MyLog.waitHere("input non numerico= " + s1);
	// // MyLog.caller("chiamante=");
	// x = Double.NaN;
	// }
	// return x;
	// }

	/**
	 * Calculation of Integral Uniformity Percentual
	 * 
	 * @param max
	 *            max signal
	 * @param min
	 *            min signal
	 * @return
	 */
	public static double uiPercCalculation(double max, double min) {
		// Ui% = ( 1 - ( signalMax - signalMin ) / ( signalMax +
		// signalMin )) * 100
		double uiPerc = (1 - (max - min) / (max + min)) * 100;
		return uiPerc;
	}

	/**
	 * Ghost roi creation and calculation
	 * 
	 * @param xRoi
	 *            x roi coordinate
	 * @param yRoi
	 *            y roi coordinate
	 * @param imp
	 *            image
	 * @param count
	 *            ghost number (for the message)
	 * @param step
	 *            step mode
	 * @return statistics
	 */
	private static ImageStatistics ghostRoi(int xRoi, int yRoi, ImagePlus imp,
			int count, boolean step, boolean test) {

		ImageStatistics stat = null;
		imp.setRoi(new OvalRoi(xRoi, yRoi, MyConst.P3_DIAM_ROI_GHOSTS,
				MyConst.P3_DIAM_ROI_GHOSTS));
		if (imp.isVisible())
			imp.getWindow().toFront();
		stat = imp.getStatistics();
		return stat;
	}

	/**
	 * Ghost percentual calculation
	 * 
	 * @param mediaGhost1
	 *            mean signal of ghost roi
	 * @param meanBkg
	 *            mean signal of background roi
	 * @param meanImage
	 *            mean signal on image roi
	 * @return
	 */
	public static double ghostPercCalculation(double mediaGhost1,
			double meanBkg, double meanImage) {
		double ghostPerc = ((mediaGhost1 - meanBkg) / meanImage) * 100.0;
		return ghostPerc;
	}

	/**
	 * Simulated 5 classes image
	 * 
	 * @param xRoi
	 *            x roi coordinate
	 * @param yRoi
	 *            y roi coordinate
	 * @param diamRoi
	 *            roi diameter
	 * @param imp
	 *            original image
	 * @param step
	 *            step-by-step mode
	 * @param test
	 *            autotest mode
	 * @return pixel counts of classes of the simulated image
	 */
	private static int[][] generaSimulata(int xRoi, int yRoi, int diamRoi,
			ImagePlus imp, String filename, boolean step, boolean verbose,
			boolean test) {

		int xRoiSimulata = xRoi + (diamRoi - MyConst.P3_DIAM_FOR_450_PIXELS)
				/ 2;
		int yRoiSimulata = yRoi + (diamRoi - MyConst.P3_DIAM_FOR_450_PIXELS)
				/ 2;
		ImagePlus impSimulata = simulata5Classi(xRoiSimulata, yRoiSimulata,
				MyConst.P3_DIAM_FOR_450_PIXELS, imp);
		if (verbose) {
			UtilAyv.showImageMaximized(impSimulata);
			ImageUtils.backgroundEnhancement(0, 0, 10, impSimulata);
		}
		impSimulata.updateAndDraw();
		int[][] classiSimulata = numeroPixelsClassi(impSimulata);
		String patName = ReadDicom.readDicomParameter(imp,
				MyConst.DICOM_PATIENT_NAME);

		String codice1 = ReadDicom.readDicomParameter(imp,
				MyConst.DICOM_SERIES_DESCRIPTION);

		String codice = UtilAyv.getFiveLetters(codice1);

		String simName = filename + patName + codice + "sim.zip";

		if (!test)

			new FileSaver(impSimulata).saveAsZip(simName);
		return classiSimulata;
	}

	/**
	 * 
	 * @param sqX
	 * @param sqY
	 * @param sqR
	 * @param imp1
	 * @return
	 */
	public static ImagePlus simulata5Classi(int sqX, int sqY, int sqR,
			ImagePlus imp1) {

		if (imp1 == null) {
			IJ.error("Simula5Classi ricevuto null");
			return (null);
		}
		int width = imp1.getWidth();
		short[] pixels1 = UtilAyv.truePixels(imp1);

		imp1.setRoi(new OvalRoi(sqX, sqY, sqR, sqR));
		ImageStatistics stat1 = imp1.getStatistics();
		imp1.deleteRoi();

		double mean = stat1.mean;
		double minus20 = mean * MyConst.MINUS_20_PERC;
		double minus10 = mean * MyConst.MINUS_10_PERC;
		double plus10 = mean * MyConst.PLUS_10_PERC;
		double plus20 = mean * MyConst.PLUS_20_PERC;
		// genero una immagine nera
		ImagePlus impSimulata = NewImage.createShortImage("Simulata", width,
				width, 1, NewImage.FILL_BLACK);
		ShortProcessor processorSimulata = (ShortProcessor) impSimulata
				.getProcessor();
		short[] pixelsSimulata = (short[]) processorSimulata.getPixels();

		short pixSorgente = 0;
		short pixSimulata = 0;
		int posizioneArrayImmagine = 0;

		for (int y = 0; y < width; y++) {
			for (int x = 0; x < width; x++) {
				posizioneArrayImmagine = y * width + x;
				pixSorgente = pixels1[posizioneArrayImmagine];
				if (pixSorgente > plus20)
					pixSimulata = MyConst.LEVEL_5;
				else if (pixSorgente > plus10)
					pixSimulata = MyConst.LEVEL_4;
				else if (pixSorgente > minus10)
					pixSimulata = MyConst.LEVEL_3;
				else if (pixSorgente > minus20)
					pixSimulata = MyConst.LEVEL_2;
				else
					pixSimulata = MyConst.LEVEL_1;
				pixelsSimulata[posizioneArrayImmagine] = pixSimulata;
			}
		}
		processorSimulata.resetMinAndMax();
		return impSimulata;
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

		int[][] vetClassi = { { MyConst.LEVEL_5, 0 }, { MyConst.LEVEL_4, 0 },
				{ MyConst.LEVEL_3, 0 }, { MyConst.LEVEL_2, 0 },
				{ MyConst.LEVEL_1, 0 } };
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
	 * Qui sono raggruppati tutti i messaggi del plugin, in questo modo è
	 * facilitata la eventuale modifica / traduzione dei messaggi.
	 * 
	 * @param select
	 * @return
	 */
	public static String listaMessaggi(int select) {
		String[] lista = new String[100];
		// ---------+-----------------------------------------------------------+
		lista[0] = "L'immagine in input viene processata con il Canny Edge Detector";
		lista[1] = "L'immagine risultante è una immagine ad 8 bit, con i soli valori \n"
				+ "0 e 255. Lo spessore del perimetro del cerchio è di 1 pixel";
		lista[2] = "Si tracciano ulteriori linee, passanti per il centro dell'immagine, \n"
				+ "su queste linee si cercano le intersezioni con il cerchio";
		lista[3] = "Analizzando il profilo del segnale lungo la linea si ricavano \n"
				+ "le coordinate delle due intersezioni con la circonferenza.";
		lista[4] = "Per tenere conto delle possibili bolle d'aria del fantoccio, si \n"
				+ "escludono dalla bisettice orizzontale il picco di sinistra e dalla \n"
				+ "bisettrice verticale il picco superiore ";
		lista[5] = "Sono mostrati in verde i punti utilizzati per il fit della circonferenza";
		lista[6] = "La circonferenza risultante dal fit è mostrata in rosso";
		lista[7] = "Il centro del fantoccio è contrassegnato dal pallino rosso";
		lista[8] = "Si determina ora l'eventuale dimensione delle bolla d'aria, \n"
				+ "essa viene automaticamente compensata entro il limite del \n"
				+ "\"maxBubbleGapLimit\"= ";
		lista[9] = "Viene mostrata la circonferenza con area 80% del fantoccio, chiamata MROI";
		lista[10] = "Il centro della MROI è contrassegnato dal pallino verde";
		lista[11] = "11";
		lista[12] = "12";
		lista[13] = "13";
		lista[14] = "E'richiesto l'intervento manuale per il posizionamento di \n"
				+ "una ROI circolare di diametro corrispondente a quello esterno \n"
				+ "del fantoccio";
		lista[15] = "Verifica posizionamento cerchio";
		lista[16] = "Troppa distanza tra i punti forniti ed il fit del cerchio";
		lista[17] = "Non si riescono a determinare le coordinate di almeno 3 punti del cerchio \n"
				+ "posizionare manualmente una ROI circolare di diametro uguale al fantoccio e\n"
				+ "premere  OK";
		lista[18] = "Eventualmente spostare la MROI circolare di area pari all'80% del fantoccio";
		lista[19] = "19";
		lista[20] = "Viene ora esaltato il segnale sul fondo, al solo scopo di mostrare \n"
				+ "la ricerca del massimo segnale per i ghosts";
		lista[21] = "La circonferenza esterna del fantoccio, determinata \n"
				+ "in precedenza è mostrata in rosso";
		lista[22] = "Il centro del fantoccio è contrassegnato dal pallino rosso";
		lista[23] = "Sono evidenziate le posizioni di massimo segnale medio, per \n"
				+ "il calcolo dei ghosts";
		lista[24] = "Valore insolito di segnale del Ghost nella posizione selezionata, \n"
				+ "modificare eventualmente la posizione \n";
		lista[25] = "25";
		lista[26] = "26";
		lista[27] = "27";
		lista[28] = "28";
		lista[29] = "29";
		lista[30] = "Ricerca della posizione per il calcolo del segnale di fondo";
		lista[31] = "La circonferenza esterna del fantoccio, determinata in precedenza \n"
				+ "è mostrata in rosso";
		lista[32] = "Il centro del fantoccio è contrassegnato dal pallino rosso";
		lista[33] = "Evidenziata la posizione per il calcolo del fondo";
		lista[34] = "Simulazione di ricerca posizione per calcolo del fondo non riuscita";
		lista[35] = "Roi per i ghost e fondo, sovrapposte alla immagine con fondo esaltato";
		lista[36] = "36";
		lista[37] = "37";
		lista[38] = "38";
		lista[39] = "39";
		// ---------+-----------------------------------------------------------+
		lista[40] = "Si calcolano le statistiche sull'area MROI, evidenziata in verde,\n"
				+ "il segnale medio vale S1= ";
		lista[41] = "Per valutare il noise, calcoliamo la immagine differenza tra \n"
				+ "le due immagini, l'immagine risultante è costituita da rumore \n"
				+ "più eventuali artefatti";
		lista[42] = "Il calcolo del rumore viene effettuato sulla immagine differenza, \n"
				+ "nell'area uguale a MROI, evidenziata in rosso, si indica con SD la \n"
				+ "Deviazione Standard di questa area SD1 = ";
		lista[43] = "Utilizzando la media del segnale sulla MROI evidenziata in verde \n"
				+ "sulla prima immagine e la deviazione standard di una identica roi evidenziata \n"
				+ "in rosso sulla immagine differenza, si calcola il rapporto Segnale/Rumore\n \n"
				+ "  snRatio = Math.sqrt(2) * S1 / SD1 \n \n  snRatio= ";
		lista[44] = "Viene generata una immagine a 5 sfumature di grigio, utilizzando \n"
				+ "come riferimento l'area evidenziata in rosso";
		lista[45] = "Questa è l'immagine simulata, i gradini di colore evidenziano le \n"
				+ "aree disuniformi";
		lista[46] = "Per il calcolo dell'Uniformità percentuale si ricavano dalla MROI anche \n"
				+ "i segnali ";
		lista[47] = "Da cui ottengo l'Uniformità Integrale Percentuale\n \n"
				+ "  uiPerc = (1 - (max - min) / (max + min)) * 100 \n \n"
				+ "  uiPerc = ";
		lista[48] = "48";
		lista[49] = "49";
		// ---------+-----------------------------------------------------------+
		lista[50] = "Per valutare i ghosts viene calcolata la statistica per ognuna delle 4 ROI, \n"
				+ "lo stesso per il fondo  \n \n  ghostPerc = ((mediaGhost - mediaFondo) / S1) * 100.0\n \n";
		lista[51] = "Spostamento automatico eccessivo per compensare la bolla \n"
				+ "d'aria presente nel fantoccio, verrà richiesto l'intervento \n"
				+ "manuale";
		lista[52] = "52";
		lista[53] = "53";
		lista[54] = "54";
		lista[55] = "55";
		lista[56] = "56";
		lista[57] = "57";
		lista[58] = "58";
		lista[59] = "59";
		// ---------+-----------------------------------------------------------+
		lista[60] = "Eventualmente modificare la circonferenza con area 80% del \n"
				+ "fantoccio, chiamata MROI";
		lista[61] = "61";
		lista[62] = "62";
		lista[63] = "63";
		lista[64] = "64";
		lista[65] = "vetMinimi==null, verificare esistenza della riga P12MIN nel file limiti.csv";
		lista[66] = "vetMaximi==null, verificare esistenza della riga P12MAX nel file limiti.csv";
		lista[67] = "67";
		lista[68] = "68";
		lista[69] = "69";
		// ---------+-----------------------------------------------------------+

		String out = lista[select];
		return out;
	}

	/**
	 * Scrive una label all'interno di una textRoi, associata alla Roi già
	 * impostata nell'ImagePlus che viene passata
	 * 
	 * @param imp1
	 * @param text
	 */
	public static void drawLabel(ImagePlus imp1, String text) {
		Roi roi1 = imp1.getRoi();
		if (roi1 == null)
			return;
		Rectangle r1 = roi1.getBounds();
		int x1 = r1.x;
		int y1 = r1.y;
		int x = x1 + 5;
		int y = y1 + 5;
		Font font;
		font = new Font("SansSerif", Font.PLAIN, 8);
		imp1.setRoi(new TextRoi(x, y, text, font));
		imp1.getRoi().setStrokeColor(Color.red);
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

		int diam = ReadDicom.readInt(Prefs.get("prefer.p12rmnDiamFantoc",
				Integer.toString(width * 2 / 3)));
		int xRoi1 = ReadDicom.readInt(Prefs.get("prefer.p12rmnXRoi1",
				Integer.toString(height / 2 - diam / 2)));
		int yRoi1 = ReadDicom.readInt(Prefs.get("prefer.p12rmnYRoi1",
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

	/**
	 * Write preferences into IJ_Prefs.txt
	 * 
	 * @param boundingRectangle
	 */
	public static void writeStoredRoiData(Rectangle boundingRectangle) {

		Prefs.set("prefer.p12rmnDiamFantoc",
				Integer.toString(boundingRectangle.width));
		Prefs.set("prefer.p12rmnXRoi1", Integer.toString(boundingRectangle.x));
		Prefs.set("prefer.p12rmnYRoi1", Integer.toString(boundingRectangle.y));
	}

}
