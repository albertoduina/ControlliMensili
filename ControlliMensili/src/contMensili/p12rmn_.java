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
 * AUTOMATIZZAZIONE di P3RMN
 * 
 * Analizza UNIFORMITA', SNR per le bobine superficiali vale per le immagini
 * 
 * circolari NOTA BENE: PLUGIN IN FASE DI SVILUPPO, NON FUNZIONANTE
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */
public class p12rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "p10_rmn_v1.10_13oct11_";

	private String TYPE = " >> CONTROLLO UNIFORMITA' IMMAGINI CIRCOLARI AUTO";

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

	public void run(String args) {

		Count c1 = new Count();
		if (!c1.jarCount("iw2ayv_"))
			return;

		String className = this.getClass().getName();

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
				boolean demo = true;
				boolean test = false;
				double profond = 30;
				mainUnifor(path1, path2, "0", "", autoCalled, step, demo, test,
						fast);

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
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[1]);
		} else {
			path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);
			path2 = TableSequence.getPath(iw2ayvTable, vetRiga[2]);
		}

		boolean step = false;
		boolean retry = false;
		// double profond = readDouble(TableSequence.getProfond(iw2ayvTable,
		// vetRiga[0]));
		// if (UtilAyv.isNaN(profond)) {
		// MyLog.logVector(iw2ayvTable[vetRiga[0]], "stringa");
		// MyLog.waitHere();
		// }

		if (fast) {
			retry = false;
			boolean autoCalled = true;
			boolean demo2 = false;
			boolean test = false;

			// MyLog.waitHere(TableSequence.getCode(iw2ayvTable, vetRiga[0])
			// + "   " + TableSequence.getCoil(iw2ayvTable, vetRiga[0])
			// + "   " + (vetRiga[0] + 1) + " / "
			// + TableSequence.getLength(iw2ayvTable));

			mainUnifor(path1, path2, autoArgs, info10, autoCalled, step, demo2,
					test, fast);

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
					// .about("Controllo Uniformità, con save UNCOMBINED e immagini circolari",
					// this.getClass());
					new AboutBox()
							.about("Controllo Uniformità, con save UNCOMBINED e immagini circolari",
									MyVersion.CURRENT_VERSION);
					retry = true;
					break;
				case 3:
					step = true;
				case 4:
					retry = false;
					boolean autoCalled = true;
					boolean verbose = false;
					boolean test = false;

					// double profond = Double.parseDouble(TableSequence
					// .getProfond(iw2ayvTable, vetRiga[0]));

					mainUnifor(path1, path2, autoArgs, info10, autoCalled,
							step, verbose, test, fast);

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
			boolean demo, boolean test, boolean fast) {

		boolean accetta = false;
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		// double angle = Double.NaN;
		boolean debug = true;

		// ===============================================================
		// Limiti "HARDWARE" non accessibili dall'esterno, utilizzati
		// per impedire che vengano accettati valori evidentemente errati
		// non provengono da APPROFONDITI studi (per quelli c'è posto in
		// AutoReports) ma da un pò di occhio
		// ================================================================
		// ================================================================
		// ================================================================
		// ================================================================

		final double minMean1 = +10;
		final double maxMean1 = +4096;
		final double minNoiseImaDiff = -2048;
		final double maxNoiseImaDiff = +2048;
		final double minSnRatio = +10;
		final double maxSnRatio = +500;
		final double minGhostPerc = -20;
		final double maxGhostPerc = +20;
		final double minUiPerc = +10;
		final double maxUiPerc = +100;
		final double maxFitError = +20;

		// ================================================================
		// ================================================================
		// ================================================================
		// ================================================================

		do {
			// ===============================================
			ImagePlus imp11 = null;
			// if (fast)
			// imp11 = UtilAyv.openImageNoDisplay(path1, true);
			// else
			if (demo)
				imp11 = UtilAyv.openImageMaximized(path1);
			else
				imp11 = UtilAyv.openImageNoDisplay(path1, true);

			if (imp11 == null)
				MyLog.waitHere("Non trovato il file " + path1);
			ImagePlus imp13 = UtilAyv.openImageNoDisplay(path2, true);
			if (imp13 == null)
				MyLog.waitHere("Non trovato il file " + path2);
			int out2[] = positionSearch11(imp11, maxFitError, info10,
					autoCalled, step, demo, test, fast);
			if (out2 == null) {
				MyLog.waitHere("out2==null");
				return null;
			}

			Overlay over11 = new Overlay();
			Overlay over11b = new Overlay();
			over11.setStrokeColor(Color.red);
			imp11.setOverlay(over11);
			// ---------------------------------
			int xCenterCircle = out2[0];
			int yCenterCircle = out2[1];
			int diamCircle = out2[2];
			// imp11.setRoi(new OvalRoi(xCenterCircle - diamCircle / 2,
			// yCenterCircle - diamCircle / 2, diamCircle, diamCircle));
			// imp11.getRoi().setStrokeColor(Color.green);
			// over11.addElement(imp11.getRoi());
			// over11b.addElement(imp11.getRoi());
			int xCenter80 = out2[3];
			int yCenter80 = out2[4];
			int diam80 = out2[5];
			// imp11.setRoi(new OvalRoi(xCenter80 - diam80 / 2, yCenter80 -
			// diam80
			// / 2, diam80, diam80));
			// imp11.getRoi().setStrokeColor(Color.red);
			// over11.addElement(imp11.getRoi());
			// =========================================
			int[] circleData = out2;
			int diamGhost = 20;
			int guard = 10;
			double[][] out3 = p12rmn_.positionSearch13(imp11, circleData,
					diamGhost, guard, "", autoCalled, step, demo, test, fast);
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
					"", autoCalled, step, demo, test, fast, irraggiungibile);
			// int[] out4 = p12rmn_.positionSearch14(imp11, circleData,
			// diamFondo,
			// guard, "", autoCalled, step, demo, test, fast,
			// irraggiungibile);
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

			// MyLog.waitHere("overlay 11");

			if (demo)
				IJ.setMinAndMax(imp11, 10, 30);

			imp11.setOverlay(over11b);
			imp11.updateAndRepaintWindow();
			if (demo)
				MyLog.waitHere(listaMessaggi(35), debug);
			// ========================================================================
			// se non ho trovato la posizione mi ritrovo qui senza out validi
			// valido, in tal caso andrebbe attivato automaticamente, sulle
			// stesse immagini p3rmn_
			// ========================================================================
			ImagePlus imp1 = null;
			ImagePlus imp2 = null;
			if (demo) {
				imp1 = UtilAyv.openImageMaximized(path1);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			} else {
				imp1 = UtilAyv.openImageMaximized(path1);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			}
			if (imp2 == null)
				MyLog.waitHere("Non trovato il file " + path2);
			// ImageWindow iw1=WindowManager.getCurrentWindow();

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
			imp1.setRoi(new OvalRoi(xCenterCircle - diamCircle / 2,
					yCenterCircle - diamCircle / 2, diamCircle, diamCircle));
			imp1.getRoi().setStrokeColor(Color.red);
			over1.addElement(imp1.getRoi());

			int xRoi2 = out2[3];
			int yRoi2 = out2[4];
			int diamRoi2 = out2[5];

			imp1.setRoi(new OvalRoi(xRoi2 - diamRoi2 / 2, yRoi2 - diamRoi2 / 2,
					diamRoi2, diamRoi2));
			// if (demo) {
			imp1.getRoi().setStrokeColor(Color.green);
			over1.addElement(imp1.getRoi());
			// }

			ImageStatistics stat1 = imp1.getStatistics();
			double mean1 = stat1.mean;
			if (demo)
				msg85percData(step, mean1);
			double uiPerc1 = uiPercCalculation(stat1.max, stat1.min);

			if (demo) {
				imp1.getRoi().setStrokeColor(Color.green);
				over1.addElement(imp1.getRoi());
				imp1.getRoi().setStrokeWidth(2);
				MyLog.waitHere(
						listaMessaggi(40)
								+ UtilAyv.printDoubleDecimals(mean1, 4), debug);
				MyLog.waitHere(
						listaMessaggi(46) + " massimo= "
								+ UtilAyv.printDoubleDecimals(stat1.max, 4)
								+ " minimo= "
								+ UtilAyv.printDoubleDecimals(stat1.min, 4),
						debug);
				MyLog.waitHere(
						listaMessaggi(47)
								+ UtilAyv.printDoubleDecimals(uiPerc1, 4)
								+ "  ", debug);
			}

			ImagePlus impDiff = UtilAyv.genImaDifference(imp1, imp2);
			Overlay overDiff = new Overlay();
			overDiff.setStrokeColor(Color.red);
			impDiff.setOverlay(overDiff);

			if (demo) {
				UtilAyv.showImageMaximized(impDiff);
				MyLog.waitHere(listaMessaggi(41), debug);
				msgElabImaDiff(step);
			}
			impDiff.setRoi(new OvalRoi(xRoi2 - diamRoi2 / 2, yRoi2 - diamRoi2
					/ 2, diamRoi2, diamRoi2));

			ImageStatistics statImaDiff = impDiff.getStatistics();

			// if (demo) {
			// impDiff.updateAndDraw();}

			// double meanImaDiff = statImaDiff.mean;
			double stdDevImaDiff = statImaDiff.stdDev;
			double noiseImaDiff = stdDevImaDiff / Math.sqrt(2);

			if (demo) {
				impDiff.getRoi().setStrokeColor(Color.green);
				impDiff.getRoi().setStrokeWidth(2);
				// overDiff.addElement(impDiff.getRoi());
				MyLog.waitHere(
						listaMessaggi(42) + stdDevImaDiff
								+ "\n \n  noise= SD / sqrt(2) \n \n  noise= "
								+ UtilAyv.printDoubleDecimals(noiseImaDiff, 4),
						debug);

			}
			// if (!test)
			// msgImaDiffData(step, meanImaDiff);

			double snRatio = Math.sqrt(2) * mean1 / stdDevImaDiff;
			if (demo) {
				MyLog.waitHere(
						listaMessaggi(43)
								+ UtilAyv.printDoubleDecimals(snRatio, 4),
						debug);
				//
				// msgSnRatio(step, uiPerc1, snRatio);
			}

			// iniziamo con il ghost inferiore

			imp1.setRoi(new OvalRoi(xGhMaxDw - diamGhost / 2, yGhMaxDw
					- diamGhost / 2, diamGhost, diamGhost));
			imp1.getRoi().setStrokeColor(Color.green);
			if (demo)
				imp1.getRoi().setStrokeWidth(2);
			over1.addElement(imp1.getRoi());
			drawLabel(imp1, "dw");
			over1.addElement(imp1.getRoi());

			imp1.setRoi(new OvalRoi(xGhMaxSx - diamGhost / 2, yGhMaxSx
					- diamGhost / 2, diamGhost, diamGhost));
			imp1.getRoi().setStrokeColor(Color.green);
			if (demo)
				imp1.getRoi().setStrokeWidth(2);
			over1.addElement(imp1.getRoi());
			drawLabel(imp1, "sx");
			over1.addElement(imp1.getRoi());

			imp1.setRoi(new OvalRoi(xGhMaxDx - diamGhost / 2, yGhMaxDx
					- diamGhost / 2, diamGhost, diamGhost));
			imp1.getRoi().setStrokeColor(Color.green);
			if (demo)
				imp1.getRoi().setStrokeWidth(2);
			over1.addElement(imp1.getRoi());
			drawLabel(imp1, "dx");
			over1.addElement(imp1.getRoi());

			imp1.setRoi(new OvalRoi(xGhMaxUp - diamGhost / 2, yGhMaxUp
					- diamGhost / 2, diamGhost, diamGhost));

			imp1.getRoi().setStrokeColor(Color.green);
			if (demo)
				imp1.getRoi().setStrokeWidth(2);
			over1.addElement(imp1.getRoi());
			drawLabel(imp1, "up");
			over1.addElement(imp1.getRoi());

			imp1.setRoi(new OvalRoi(xCenterFondo - diamFondo / 2, yCenterFondo
					- diamFondo / 2, diamFondo, diamFondo));
			imp1.getRoi().setStrokeColor(Color.orange);
			if (demo)
				imp1.getRoi().setStrokeWidth(2);
			over1.addElement(imp1.getRoi());
			drawLabel(imp1, "bkg");
			over1.addElement(imp1.getRoi());

			imp1.deleteRoi();

			if (demo)
				UtilAyv.autoAdjust(imp1, imp1.getProcessor());

			diamGhost = MyConst.P3_DIAM_ROI_GHOSTS;
			int xRoi5 = (int) Math.round(xGhMaxDw - diamGhost / 2);
			int yRoi5 = (int) Math.round(yGhMaxDw - diamGhost / 2);
			ImageStatistics statGh1 = ghostRoi(xRoi5, yRoi5, imp1, 1, step,
					test);
			double mediaGhost1 = statGh1.mean;

			int xRoi6 = (int) Math.round(xGhMaxSx - diamGhost / 2);
			int yRoi6 = (int) Math.round(yGhMaxSx - diamGhost / 2);

			ImageStatistics statGh2 = ghostRoi(xRoi6, yRoi6, imp1, 2, step,
					test);
			double mediaGhost2 = statGh2.mean;

			int xRoi7 = (int) Math.round(xGhMaxDx - diamGhost / 2);
			int yRoi7 = (int) Math.round(yGhMaxDx - diamGhost / 2);
			ImageStatistics statGh3 = ghostRoi(xRoi7, yRoi7, imp1, 3, step,
					test);
			double mediaGhost3 = statGh3.mean;

			int xRoi8 = (int) Math.round(xGhMaxUp - diamGhost / 2);
			int yRoi8 = (int) Math.round(yGhMaxUp - diamGhost / 2);

			ImageStatistics statGh4 = ghostRoi(xRoi8, yRoi8, imp1, 4, step,
					test);
			double mediaGhost4 = statGh4.mean;

			int xRoi9 = xCenterFondo - MyConst.P12_DIAM_ROI_BACKGROUND / 2;
			int yRoi9 = yCenterFondo - MyConst.P12_DIAM_ROI_BACKGROUND / 2;

			ImageStatistics statBkg = UtilAyv.backCalc(xRoi9, yRoi9,
					MyConst.P12_DIAM_ROI_BACKGROUND, imp1, step, true, true);
			double meanBkg = statBkg.mean;

			double ghostPerc1 = ghostPercCalculation(mediaGhost1, meanBkg,
					mean1);

			double ghostPerc2 = ghostPercCalculation(mediaGhost2, meanBkg,
					mean1);
			double ghostPerc3 = ghostPercCalculation(mediaGhost3, meanBkg,
					mean1);
			double ghostPerc4 = ghostPercCalculation(mediaGhost4, meanBkg,
					mean1);

			// MyLog.waitHere("out2[0]= " + out2[0] + "\nout2[1]= " + out2[1]
			// + "\nout2[2]= " + out2[2] + "\nout2[3]=" + out2[3]
			// + "\nout2[4]= " + out2[4] + "\nout2[5]= " + out2[5]);

			if (demo) {
				MyLog.waitHere(
						listaMessaggi(50) + "  ghostPerc1= "
								+ UtilAyv.printDoubleDecimals(ghostPerc1, 4)
								+ "\n" + "  ghostPerc2= "
								+ UtilAyv.printDoubleDecimals(ghostPerc2, 4)
								+ "\n" + "  ghostPerc3= "
								+ UtilAyv.printDoubleDecimals(ghostPerc3, 4)
								+ "\n" + "  ghostPerc4= "
								+ UtilAyv.printDoubleDecimals(ghostPerc4, 4),
						debug);
			}

			if (demo)
				MyLog.waitHere(listaMessaggi(44), debug);

			int[][] classiSimulata = generaSimulata(xCenter80 - diam80 / 2,
					yCenter80 - diam80 / 2, diam80, imp1, fileDir, step, demo,
					test);
			imp1.deleteRoi();

			String[][] tabCodici = TableCode.loadTableCSV(MyConst.CODE_FILE);
			if (demo)
				MyLog.waitHere(listaMessaggi(45), debug);
			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1,
					imp1, tabCodici, VERSION, autoCalled);
			// put values in ResultsTable
			rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);

			String patName = ReadDicom.readDicomParameter(imp1,
					MyConst.DICOM_PATIENT_NAME);

			String codice1 = ReadDicom.readDicomParameter(imp1,
					MyConst.DICOM_SERIES_DESCRIPTION);

			String codice = UtilAyv.getFiveLetters(codice1);

			simulataName = fileDir + patName + codice + "sim.zip";

			if (imp1.isVisible())
				imp1.getWindow().toFront();

			// =================================================================
			// Effettuo dei controlli "di sicurezza" sui valori calcolati,
			// in modo da evitare possibili sorprese
			// ================================================================

			if (!UtilAyv.checkLimits(mean1, minMean1, maxMean1))
				MyLog.waitHere("limite programmato mean1 SUPERATO \n \n mean1= " + mean1);
			if (!UtilAyv.checkLimits(noiseImaDiff, minNoiseImaDiff,
					maxNoiseImaDiff))
				MyLog.waitHere("limite programmato noiseImaDiff SUPERATO  \n \n noiseImaDiff= "
						+ noiseImaDiff);
			if (!UtilAyv.checkLimits(snRatio, minSnRatio, maxSnRatio))
				MyLog.waitHere("limite programmato snRatio SUPERATO \n \n snRatio= "
						+ snRatio);
			if (!UtilAyv.checkLimits(ghostPerc1, minGhostPerc, maxGhostPerc))
				MyLog.waitHere("limite programmato ghostPerc1 SUPERATO \n \n ghostPerc1= "
						+ ghostPerc1);
			if (!UtilAyv.checkLimits(ghostPerc2, minGhostPerc, maxGhostPerc))
				MyLog.waitHere("limite programmato ghostPerc2 SUPERATO \n \n ghostPerc2= "
						+ ghostPerc2);
			if (!UtilAyv.checkLimits(ghostPerc3, minGhostPerc, maxGhostPerc))
				MyLog.waitHere("limite programmato ghostPerc3 SUPERATO \n \n ghostPerc3= "
						+ ghostPerc3);
			if (!UtilAyv.checkLimits(ghostPerc4, minGhostPerc, maxGhostPerc))
				MyLog.waitHere("limite programmato ghostPerc4 SUPERATO \n \n ghostPerc4= "
						+ ghostPerc4);
			if (!UtilAyv.checkLimits(uiPerc1, minUiPerc, maxUiPerc))
				MyLog.waitHere("limite programmato uiPerc1 SUPERATO \n \n uiPerc1= "
						+ uiPerc1);

			IJ.wait(1000);
			// MyLog.waitHere();
			//
			rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);
			int col = 2;
			String t1 = "TESTO          ";
			// put values in ResultsTable
			rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);
			rt.setHeading(++col, "roi_x");
			rt.setHeading(++col, "roi_y");
			rt.setHeading(++col, "roi_b");
			rt.setHeading(++col, "roi_h");

			rt.addLabel(t1, "Segnale");
			rt.addValue(2, mean1);
			rt.addValue(3, stat1.roiX);
			rt.addValue(4, stat1.roiY);
			rt.addValue(5, stat1.roiWidth);
			rt.addValue(6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Rumore");
			rt.addValue(2, noiseImaDiff);
			rt.addValue(3, stat1.roiX);
			rt.addValue(4, stat1.roiY);
			rt.addValue(5, stat1.roiWidth);
			rt.addValue(6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "SNR");
			rt.addValue(2, snRatio);
			rt.addValue(3, stat1.roiX);
			rt.addValue(4, stat1.roiY);
			rt.addValue(5, stat1.roiWidth);
			rt.addValue(6, stat1.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Ghost_1");
			rt.addValue(2, ghostPerc1);
			rt.addValue(3, statGh1.roiX);
			rt.addValue(4, statGh1.roiY);
			rt.addValue(5, statGh1.roiWidth);
			rt.addValue(6, statGh1.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Ghost_2");
			rt.addValue(2, ghostPerc2);
			rt.addValue(3, statGh2.roiX);
			rt.addValue(4, statGh2.roiY);
			rt.addValue(5, statGh2.roiWidth);
			rt.addValue(6, statGh2.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Ghost_3");
			rt.addValue(2, ghostPerc3);
			rt.addValue(3, statGh3.roiX);
			rt.addValue(4, statGh3.roiY);
			rt.addValue(5, statGh3.roiWidth);
			rt.addValue(6, statGh3.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Ghost_4");
			rt.addValue(2, ghostPerc4);
			rt.addValue(3, statGh4.roiX);
			rt.addValue(4, statGh4.roiY);
			rt.addValue(5, statGh4.roiWidth);
			rt.addValue(6, statGh4.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "Unif.Integr.%");
			rt.addValue(2, uiPerc1);
			rt.addValue(3, stat1.roiX);
			rt.addValue(4, stat1.roiY);
			rt.addValue(5, stat1.roiWidth);
			rt.addValue(6, stat1.roiHeight);

			String[] levelString = { "+20%", "+10%", "-10%", "-20%", "-30%",
					"-40%", "-50%", "-60%", "-70%", "-80%", "-90%", "fondo" };

			for (int i1 = 0; i1 < classiSimulata.length; i1++) {
				rt.incrementCounter();
				rt.addLabel(t1, ("Classe" + classiSimulata[i1][0]) + "_"
						+ levelString[i1]);
				rt.addValue(2, classiSimulata[i1][1]);
			}

			if (demo && !test && !fast) {
				rt.show("Results");
			}

			if (fast || autoCalled) {
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

	public static boolean selfTestGe(boolean verbose) {
		boolean ok = selfTestSiemens(verbose);
		return ok;
	}

	public static boolean selfTestSiemens(boolean verbose) {
		double[] vetReference = null;
		String[] list = { "C001_testP10", "C002_testP10" };
		String[] path = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);

		String path1 = path[0];
		String path2 = path[1];

		String autoArgs = "0";
		boolean autoCalled = false;
		boolean step = true;
		boolean test = true;
		boolean fast = false;

		ResultsTable rt1 = mainUnifor(path1, path2, autoArgs, "", autoCalled,
				step, verbose, test, fast);
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
	/**
	 * Siemens test image expected results
	 * 
	 * @return
	 */
	double[] referenceSiemens() {

		double mean = 1680.5405176520405;
		double noise = 4.071338700948202;
		double snRatio = 412.77344900355456;
		double g5 = 0.1619430747878742;
		double g6 = -0.1355802486596156;
		double g7 = -0.11674965856800232;
		double g8 = 0.21542195064805592;
		double uiPerc = 91.53798641136504;

		double c4 = 0.0;
		double c3 = 0.0;
		double c2 = 22558.0;
		double c1 = 379.0;
		double c0 = 42599.0;
		double[] vetReference = { mean, noise, snRatio, g5, g6, g7, g8, uiPerc,
				c4, c3, c2, c1, c0 };
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
		double backNoise = 12.225;
		double snRatio = 35.48765967441802;
		double fwhm = 11.43429317989865;
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
		double[] profi1 = ((Line) roi1).getPixels(); // profilo non mediato
		profi1[profi1.length - 1] = 0; // azzero a mano l'ultimo pixel
		if (step) {
			imp1.updateAndDraw();
			ButtonMessages.ModelessMsg("Profilo non mediato  <50>", "CONTINUA");
		}
		return (profi1);
	}

	/**
	 * Mostra a video un profilo con linea a metà picco
	 * 
	 * @param profile1
	 *            Vettore con il profilo da analizzare
	 * @param bslab
	 *            Flag slab che qui mettiamo sempre true
	 */

	private static void createPlot2(double profile1[], boolean bslab,
			boolean bLabelSx, String title) {

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
		Plot plot = new Plot("Profilo penetrazione__" + title, "pixel",
				"valore", xcoord1, profile1);
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

		plot.addLabel(labelPosition, 0.40, title);
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
	}

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
	 * Author: Eli Billauer
	 * 
	 * @param profile
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
		matout.add(mintabx);
		matout.add(mintaby);
		matout.add(maxtabx);
		matout.add(maxtaby);

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

		// Overlay over1 = new Overlay();
		// over1.setStrokeColor(Color.red);
		// imp1.setOverlay(over1);

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
		// MyLog.logMatrixVertical(line1, title);
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

			// MyLog.waitHere();

			// Window win1 = WindowManager.getWindow("Profile");
			// Window win2 = imp1.getWindow();
			// Point p1 = win2.getLocation();
			// // MyLog.waitHere("p1= " + p1);
			// // win1.setLocationRelativeTo(win2);
			// // win1.setLocation(p1);
			// win1.setLocation(p1.x - win1.getWidth(), p1.y);

			// win1.setVisible(false);

			// plotPoints(imp1, over1, peaks1);
			if (demo)
				MyLog.waitHere(listaMessaggi(3), debug);

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

		// verifico di avere trovato un max di 2 picchi
		if (peaks1[2].length > 2)
			MyLog.waitHere("Attenzione trovati troppe intersezioni col cerchio, VERIFICARE");

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

	/***
	 * * Ricerca della posizione della ROI per il calcolo dell'uniformità,
	 * utilizzando il Canny Edge Detector
	 * 
	 * @param imp11
	 *            immagine di input
	 * @param profond
	 *            profondità ROI
	 * @param direction
	 *            direzione in cui eventualmente trovo la bolla (si può ricavare
	 *            anche dai dati dicom) 0= nessuna, 1= alto 2 sinistra .... poi
	 *            nin zò
	 * @param info1
	 *            messaggio esplicativo
	 * @param autoCalled
	 *            flag true se chiamato in automatico
	 * @param step
	 *            flag true se funzionamento passo - passo
	 * @param verbose
	 *            flag true se funzionamento verbose
	 * @param test
	 *            flag true se in test, non vengono visualizzate immagini e non
	 *            viene chiesta alcuna conferma
	 * @param fast
	 *            flag true se modo batch
	 * @return vettore con dati ROI
	 */

	/**
	 * Ricerca posizione ROI per calcolo uniformità. Versione con Canny Edge
	 * Detector
	 * 
	 * @param imp11
	 * @param info1
	 * @param autoCalled
	 * @param step
	 * @param verbose
	 * @param test
	 * @param fast
	 * @return
	 */
	public static int[] positionSearch11(ImagePlus imp11, double maxFitError,
			String info1, boolean autoCalled, boolean step, boolean demo,
			boolean test, boolean fast) {
		//
		// ================================================================================
		// Inizio calcoli geometrici
		// ================================================================================
		//

		boolean debug = true;

		int xCenterCircle = 0;
		int yCenterCircle = 0;
		int xCenterCircleMan = 0;
		int yCenterCircleMan = 0;
		int diamCircleMan = 0;
		int xCenterCircleMan80 = 0;
		int yCenterCircleMan80 = 0;
		int diamCircleMan80 = 0;
		int diamCircle = 0;
		int xRoi2 = 0;
		int yRoi2 = 0;
		int diamRoi2 = 0;
		int xcorr = 0;
		int ycorr = 0;

		int height = imp11.getHeight();
		int width = imp11.getWidth();
		int[] roiData = readPreferences(width, height, MyConst.P3_ROI_LIMIT);
		int diamRoiMan = roiData[2];

		Overlay over12 = new Overlay();

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(
						ReadDicom.readDicomParameter(imp11,
								MyConst.DICOM_PIXEL_SPACING), 1));

		if (demo) {
			UtilAyv.showImageMaximized(imp11);

			MyLog.waitHere(listaMessaggi(0), debug);

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
			MyLog.waitHere(listaMessaggi(1), debug);
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
				"BISETTRICE DIAGONALE SINISTRA", demo, demo, debug);
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
		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);
		peaks6 = cannyProfileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE DESTRA", false, false, false);
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
		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);

		peaks1 = cannyProfileAnalyzer(imp12, dimPixel,
				"BISETTRICE ORIZZONTALE", showProfiles, false, false);
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

		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);
		peaks2 = cannyProfileAnalyzer(imp12, dimPixel, "BISETTRICE VERTICALE",
				showProfiles, false, false);
		// PLOTTAGGIO PUNTI
		if (peaks2 != null)
			plotPoints(imp12, over12, peaks2);

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
				showProfiles, false, false);
		// PLOTTAGGIO PUNTI
		if (peaks3 != null)
			plotPoints(imp12, over12, peaks3);

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
				showProfiles, false, false);
		// PLOTTAGGIO PUNTI
		if (peaks4 != null)
			plotPoints(imp12, over12, peaks4);
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
				"BISETTRICE DIAGONALE DESTRA EXTRA", showProfiles, false, false);
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
		if (strokewidth)
			imp12.getRoi().setStrokeWidth(strWidth);
		peaks8 = cannyProfileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE DESTRA EXTRA", showProfiles, false, false);
		// PLOTTAGGIO PUNTI
		if (peaks8 != null)
			plotPoints(imp12, over12, peaks8);
		// --------------------------------------------
		if (demo) {
			MyLog.waitHere(listaMessaggi(2), debug);
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
			MyLog.waitHere(listaMessaggi(4), debug);
		// della bisettice orizzontale prendo solo il picco di dx
		if (npeaks1 == 1) {
			j1++;
			xPoints3[j1] = (int) (peaks1[2][1]);
			yPoints3[j1] = (int) (peaks1[3][1]);
		}
		// della bisettice verticale prendo solo il picco in basso
		if (npeaks2 == 1) {
			j1++;
			xPoints3[j1] = (int) (peaks2[2][1]);
			yPoints3[j1] = (int) (peaks2[3][1]);
		}
		for (int i1 = 0; i1 < npeaks3; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks3[2][i1]);
			yPoints3[j1] = (int) (peaks3[3][i1]);
		}
		for (int i1 = 0; i1 < npeaks4; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks4[2][i1]);
			yPoints3[j1] = (int) (peaks4[3][i1]);
		}
		for (int i1 = 0; i1 < npeaks5; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks5[2][i1]);
			yPoints3[j1] = (int) (peaks5[3][i1]);
		}
		for (int i1 = 0; i1 < npeaks6; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks6[2][i1]);
			yPoints3[j1] = (int) (peaks6[3][i1]);
		}
		for (int i1 = 0; i1 < npeaks7; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks7[2][i1]);
			yPoints3[j1] = (int) (peaks7[3][i1]);
		}
		for (int i1 = 0; i1 < npeaks8; i1++) {
			j1++;
			xPoints3[j1] = (int) (peaks8[2][i1]);
			yPoints3[j1] = (int) (peaks8[3][i1]);
		}
		over12.clear();

		// ----------------------------------------------------------------------
		// Verifica di avere trovato almeno 3 punti, altrimenti chiede la
		// selezione manuale del cerchio
		// -------------------------------------------------------------------

		if (xPoints3.length < 3 || test) {
			// ==================================================================
			// INTERVENTO MANUALE PER CASI DISPERATI, SAN GENNARO PENSACI TU
			// ==================================================================
			fast = false;
			manualOverride = true;
			UtilAyv.showImageMaximized(imp11);
			imp11.setRoi(new OvalRoi((width / 2) - diamRoiMan / 2, (height / 2)
					- diamRoiMan / 2, diamRoiMan, diamRoiMan));
			MyLog.waitHere(listaMessaggi(17), debug);
			Rectangle boundRec11 = imp11.getProcessor().getRoi();
			xCenterCircleMan = Math.round(boundRec11.x + boundRec11.width / 2);
			yCenterCircleMan = Math.round(boundRec11.y + boundRec11.height / 2);
			diamCircleMan = boundRec11.width;
			diamCircleMan80 = (int) Math.round(diamCircleMan
					* MyConst.P3_AREA_PERC_80_DIAM);
			imp11.setRoi(new OvalRoi(xCenterCircleMan - diamCircleMan80 / 2,
					yCenterCircleMan - diamCircleMan80 / 2, diamCircleMan80,
					diamCircleMan80));
			MyLog.waitHere(listaMessaggi(18), debug);
			Rectangle boundRec111 = imp11.getProcessor().getRoi();
			xCenterCircleMan80 = Math.round(boundRec111.x + boundRec111.width
					/ 2);
			yCenterCircleMan80 = Math.round(boundRec111.y + boundRec111.height
					/ 2);
			diamCircleMan80 = boundRec111.width;
		} else {
			imp12.setRoi(new PointRoi(xPoints3, yPoints3, xPoints3.length));
			if (demo) {
				imp12.updateAndDraw();
				over12.addElement(imp12.getRoi());
				over12.setStrokeColor(Color.green);
				imp12.setOverlay(over12);
				imp12.updateAndDraw();
				MyLog.waitHere(listaMessaggi(5), debug);
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
				MyLog.waitHere(listaMessaggi(6), debug);
			Rectangle boundRec = imp12.getProcessor().getRoi();
			xCenterCircle = Math.round(boundRec.x + boundRec.width / 2);
			yCenterCircle = Math.round(boundRec.y + boundRec.height / 2);
			diamCircle = boundRec.width;
			if (!manualOverride)
				writeStoredRoiData(boundRec);

			drawCenter(imp12, over12, xCenterCircle, yCenterCircle, Color.red);

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
				MyLog.waitHere(listaMessaggi(16), debug);
			}

			//
			// ----------------------------------------------------------
			// disegno la ROI del centro, a solo scopo dimostrativo !
			// ----------------------------------------------------------
			//

			if (demo) {
				drawCenter(imp12, over12, xCenterCircle, yCenterCircle,
						Color.red);
				MyLog.waitHere(listaMessaggi(7), debug);

			}

			imp12.setRoi(new Line(xCenterCircle, 0, xCenterCircle, height));
			if (demo) {
				imp12.updateAndDraw();
				over12.addElement(imp12.getRoi());
			}
			peaks9 = cannyProfileAnalyzer(imp12, dimPixel,
					"BISETTRICE VERTICALE", showProfiles, false, false);

			// PLOTTAGGIO PUNTI

			double gapVert = 0;
			if (peaks9 != null) {
				plotPoints(imp12, over12, peaks9);
				gapVert = diamCircle / 2 - (yCenterCircle - peaks9[3][0]);
			}
			imp12.setRoi(new Line(0, yCenterCircle, width, yCenterCircle));
			if (demo) {
				imp12.updateAndDraw();
				over12.addElement(imp12.getRoi());
			}
			peaks10 = cannyProfileAnalyzer(imp12, dimPixel,
					"BISETTRICE VERTICALE", showProfiles, false, false);

			double gapOrizz = 0;
			if (peaks10 != null) {
				plotPoints(imp12, over12, peaks10);
				gapOrizz = diamCircle / 2 - (xCenterCircle - peaks10[2][0]);
			}

			if (demo)
				MyLog.waitHere(listaMessaggi(8), debug);

			if (gapOrizz > gapVert) {
				xcorr = (int) gapOrizz / 2;

			} else {
				ycorr = (int) gapVert / 2;
			}
			diamRoi2 = (int) Math.round(diamCircle
					* MyConst.P3_AREA_PERC_80_DIAM);

			xRoi2 = xCenterCircle + xcorr - diamRoi2 / 2;
			yRoi2 = yCenterCircle + ycorr - diamRoi2 / 2;

			// TODO verificare che con questo spostamento il cerchio abbia una
			// certa
			// distanza sia sotto che sopra

			// int d1 = (int) (peaks9[2][0] - (xCenterCircle + xcorr - diamRoi2
			// /
			// 2));
			// int d2 = (int) (peaks9[2][0] - (xCenterCircle + xcorr + diamRoi2
			// /
			// 2));
			// int d3 = (int) (peaks10[2][0] - (yCenterCircle + ycorr - diamRoi2
			// /
			// 2));
			// int d4 = (int) (peaks10[2][0] - (yCenterCircle + ycorr + diamRoi2
			// /
			// 2));

			// MyLog.waitHere("d1= " + d1 + " d2= " + d2 + " d3=" + d3 + " d4= "
			// +
			// d4);

			imp12.setRoi(new OvalRoi(xRoi2, yRoi2, diamRoi2, diamRoi2));
			Rectangle boundingRectangle2 = imp12.getProcessor().getRoi();
			diamRoi2 = (int) boundingRectangle2.width;
			xRoi2 = boundingRectangle2.x + boundingRectangle2.width / 2;
			yRoi2 = boundingRectangle2.y + boundingRectangle2.height / 2;
			// imp12.killRoi();
		}

		if (manualOverride) {
			// carico qui i dati dell'avvenuto posizionamento manuale
			xCenterCircle = xCenterCircleMan;
			yCenterCircle = yCenterCircleMan;
			diamCircle = diamCircleMan;

			xRoi2 = xCenterCircleMan80;
			yRoi2 = yCenterCircleMan80;
			diamRoi2 = diamCircleMan80;
			imp12.setRoi(new OvalRoi(xRoi2, yRoi2, diamRoi2, diamRoi2));
		}

		if (demo) {
			imp12.updateAndDraw();
			imp12.getRoi().setStrokeColor(Color.green);
			over12.addElement(imp12.getRoi());
			MyLog.waitHere(listaMessaggi(9), debug);
			drawCenter(imp12, over12, xCenterCircle + xcorr, yCenterCircle
					+ ycorr, Color.green);
			MyLog.waitHere(listaMessaggi(10), debug);
		}

		// imp11.setRoi(new OvalRoi(xCenterCircle - diamCircle / 2,
		// yCenterCircle
		// - diamCircle / 2, diamCircle, diamCircle));
		//
		// if (demo)
		// imp11.getRoi().setStrokeColor(Color.red);

		// diamRoi2 = (int) boundingRectangle2.width;
		// xRoi2 = boundingRectangle2.x + boundingRectangle2.width / 2;
		// yRoi2 = boundingRectangle2.y + boundingRectangle2.height / 2;
		// imp12.killRoi();

		imp12.close();
		int[] out2 = new int[6];
		out2[0] = xCenterCircle;
		out2[1] = yCenterCircle;
		out2[2] = diamCircle;
		out2[3] = xRoi2;
		out2[4] = yRoi2;
		out2[5] = diamRoi2;
		return out2;
	}

	/***
	 * * Ricerca della posizione della ROI per il calcolo dell'uniformità
	 * 
	 * @param imp11
	 *            immagine di input
	 * @param profond
	 *            profondità ROI
	 * @param direction
	 *            direzione in cui eventualmente trovo la bolla (si può ricavare
	 *            anche dai dati dicom) 0= nessuna, 1= alto 2 sinistra .... poi
	 *            nin zò
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
	public static int[] positionSearch12(ImagePlus imp11, ImagePlus imp13,
			String info1, boolean autoCalled, boolean step, boolean verbose,
			boolean test, boolean fast) {
		//
		// ================================================================================
		// Inizio calcoli geometrici
		// ================================================================================
		//

		int xCenterCircle = 0;
		int yCenterCircle = 0;
		Overlay over12 = new Overlay();

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(
						ReadDicom.readDicomParameter(imp11,
								MyConst.DICOM_PIXEL_SPACING), 1));

		int width = imp11.getWidth();
		int height = imp11.getHeight();

		ImagePlus imp12 = imp11.duplicate();
		if (step)
			UtilAyv.showImageMaximized(imp12);

		//
		// -------------------------------------------------
		// Determinazione del cerchio
		// -------------------------------------------------
		//
		ImageProcessor ip12 = imp12.getProcessor();
		ip12.smooth();
		if (step)
			new WaitForUserDialog("Eseguito SMOOTH").show();
		ip12.findEdges();

		if (step)
			new WaitForUserDialog("Eseguito FIND EDGES").show();

		imp12.setOverlay(over12);

		double[][] peaks1 = new double[4][1];
		double[][] peaks2 = new double[4][1];
		double[][] peaks3 = new double[4][1];
		double[][] peaks4 = new double[4][1];
		boolean showProfiles = step;

		showProfiles = false;

		// IJ.log("BISETTRICE ORIZZONTALE");
		imp12.setRoi(new Line(0, height / 2, width, height / 2));
		if (step) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
			over12.setStrokeColor(Color.red);
		}
		peaks1 = profileAnalyzer(imp12, dimPixel, "BISETTRICE ORIZZONTALE",
				showProfiles);

		// IJ.log("BISETTRICE VERTICALE");
		imp12.setRoi(new Line(width / 2, 0, width / 2, height));
		if (step) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}
		peaks2 = profileAnalyzer(imp12, dimPixel, "BISETTRICE VERTICALE",
				showProfiles);
		// IJ.log("DIAGONALE 1");
		imp12.setRoi(new Line(0, 0, width, height));
		if (step) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}
		peaks3 = profileAnalyzer(imp12, dimPixel,
				"BISETTRICE DIAGONALE SINISTRA", showProfiles);
		// IJ.log("DIAGONALE 2");
		imp12.setRoi(new Line(0, width, height, 0));
		if (step) {
			imp12.updateAndDraw();
			over12.addElement(imp12.getRoi());
		}
		peaks4 = profileAnalyzer(imp12, dimPixel,
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

		over12.clear();

		// ----------------------------------------------------------------------
		// Verifica di avere trovato almeno 3 punti, altrimenti chiede la
		// selezione manuale del cerchio
		// -------------------------------------------------------------------
		if (xPoints3.length >= 3) {
			imp12.setRoi(new PointRoi(xPoints3, yPoints3, xPoints3.length));
			if (step) {
				imp12.updateAndDraw();
				over12.addElement(imp12.getRoi());
				over12.setStrokeColor(Color.red);
				imp12.setOverlay(over12);
				imp12.updateAndDraw();
				new WaitForUserDialog("Premere OK").show();
			}
			// eseguo ora fitCircle per trovare centro e dimensione del
			// fantoccio
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
			new WaitForUserDialog(
					"Non si riescono a determinare le coordinate di almeno 3 punti del cerchio,\nposizionare a mano una ROI circolare di diametro uguale al fantoccio e\npremere  OK")
					.show();
			//
			// Ho così risolto la mancata localizzazione automatica del
			// fantoccio
			//
		}

		Rectangle boundRec = imp12.getProcessor().getRoi();

		// x1 ed y1 sono le due coordinate del centro

		xCenterCircle = boundRec.x + boundRec.width / 2;
		yCenterCircle = boundRec.y + boundRec.height / 2;
		int diamCircle = boundRec.width;

		//
		// ----------------------------------------------------------
		// disegno la ROI del centro, a solo scopo dimostrativo !
		// ----------------------------------------------------------
		//
		over12.setStrokeColor(Color.red);
		imp12.setOverlay(over12);

		if (verbose) {
			imp12.setRoi(new OvalRoi(xCenterCircle - 4, yCenterCircle - 4, 8, 8));
			Roi roi1 = imp12.getRoi();
			if (roi1 == null)
				IJ.log("roi1==null");
			over12.addElement(imp12.getRoi());
			over12.setStrokeColor(Color.red);

			imp12.killRoi();
		}

		if (step)
			MyLog.waitHere("Determinazione dati per la ROI all'80%");

		int diamRoi2 = (int) Math.round(diamCircle
				* MyConst.P3_AREA_PERC_80_DIAM);
		int xRoi2 = xCenterCircle - diamRoi2 / 2;
		int yRoi2 = yCenterCircle - diamRoi2 / 2;

		imp12.setRoi(new OvalRoi(xRoi2, yRoi2, diamRoi2, diamRoi2));

		// Ora posso chiedere di riposizionare la ROI 80%, oppure automatizzerò
		// la (rara) richiesta;

		if (step)
			msgRoi85percPositioning();

		Rectangle boundingRectangle2 = imp12.getProcessor().getRoi();
		diamRoi2 = (int) boundingRectangle2.width;
		// xRoi2 = boundingRectangle2.x
		// + ((boundingRectangle2.width - diamRoi2) / 2);
		// yRoi2 = boundingRectangle2.y
		// + ((boundingRectangle2.height - diamRoi2) / 2);
		xRoi2 = boundingRectangle2.x + boundingRectangle2.width / 2;
		yRoi2 = boundingRectangle2.y + boundingRectangle2.height / 2;

		// imp12.deleteRoi();imp12.updateAndDraw();
		// MyLog.waitHere("pulito???");

		// Ridisegno la Roi 80% al posto che poi restituirò

		// imp12.setRoi(new OvalRoi(xRoi2, yRoi2, diamRoi2, diamRoi2));
		//
		// MyLog.waitHere("roi 80% dopo il riposizionamento: xRoi2= "+xRoi2+" yRoi2= "+yRoi2+" diamRoi2= "+diamRoi2);

		int[] out2 = new int[6];
		out2[0] = xCenterCircle;
		out2[1] = yCenterCircle;
		out2[2] = diamCircle;

		out2[3] = xRoi2;
		out2[4] = yRoi2;
		out2[5] = diamRoi2;

		return out2;
	}

	/***
	 * Posizionamento automatico delle roi su cui calcoleremo i ghosts
	 * 
	 */

	public static double[][] positionSearch13(ImagePlus imp1, int[] circleData,
			int diamGhost, int guard, String info1, boolean autoCalled,
			boolean step, boolean demo, boolean test, boolean fast) {

		// leggo i dati del cerchio "esterno" del fantoccio e li plotto
		// sull'immagine

		boolean demo1 = demo;
		boolean debug = true;

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

		int xCenterCircle = circleData[0];
		int yCenterCircle = circleData[1];
		int diamCircle = circleData[2];
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
			MyLog.waitHere(listaMessaggi(20), debug);
		}

		// marco con un punto il centro del fantoccio

		if (demo)
			drawCenter(imp2, over2, xCenterCircle, yCenterCircle, Color.red);

		// imp2.setRoi(new OvalRoi(xCenterCircle - 2, yCenterCircle - 2, 4, 4));
		// if (demo) {
		//
		// UtilAyv.showImageMaximized(imp2);
		// if (demo) {
		// imp2.getRoi().setStrokeColor(Color.red);
		// over2.addElement(imp2.getRoi());
		// MyLog.waitHere(listaMessaggi(21), debug);
		// }
		// }

		if (demo)
			IJ.setMinAndMax(imp2, 10, 100);

		// ghost di sinistra

		// IJ.log("ghost di sinistra");

		int critic_0;
		double ghMaxSx = -99999;
		int xGhMaxSx = -9999;
		int yGhMaxSx = -9999;

		for (int i1 = 0; i1 < diamCircle - diamGhost; i1++) {
			for (int i2 = 0; i2 < diamCircle - diamGhost; i2++) {
				int px = i2;
				int py = i1 + (yCenterCircle - diamCircle / 2);
				int xcentGhost = px + diamGhost / 2;
				int ycentGhost = py + diamGhost / 2;
				critic_0 = criticalDistanceCalculation(xcentGhost, ycentGhost,
						diamGhost / 2, xCenterCircle, yCenterCircle,
						diamCircle / 2);
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

		// IJ.log("il massimo del ghost di sinistra si trova a x= " + xGhMaxSx
		// + " y= " + yGhMaxSx + " mean= " + ghMaxSx);

		// MyLog.waitHere("AAAAAA per conferma la media è= "
		// + imp2.getStatistics().mean);

		// ora, dei ghost mi interessa solo il segnale .........

		// ghost superiore
		// IJ.log("ghost superiore");
		int critic_1;
		double ghMaxUp = -99999;
		int xGhMaxUp = -9999;
		int yGhMaxUp = -9999;

		for (int i1 = 0; i1 < diamCircle - diamGhost; i1++) {
			for (int i2 = 0; i2 < diamCircle - diamGhost; i2++) {
				int py = i2;
				int px = i1 + (xCenterCircle - diamCircle / 2);
				int xcentGhost = px + diamGhost / 2;
				int ycentGhost = py + diamGhost / 2;
				critic_1 = criticalDistanceCalculation(xcentGhost, ycentGhost,
						diamGhost / 2, xCenterCircle, yCenterCircle,
						diamCircle / 2);
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
		// IJ.log("ghost di destra");

		int critic_2;
		double ghMaxDx = -99999;
		int xGhMaxDx = -9999;
		int yGhMaxDx = -9999;

		for (int i2 = 0; i2 < diamCircle - diamGhost; i2++) {
			for (int i1 = width - diamGhost; i1 > diamCircle - diamGhost; i1--) {
				int px = i1;
				int py = i2 + (yCenterCircle - diamCircle / 2);
				int xcentGhost = px + diamGhost / 2;
				int ycentGhost = py + diamGhost / 2;
				critic_2 = criticalDistanceCalculation(xcentGhost, ycentGhost,
						diamGhost / 2, xCenterCircle, yCenterCircle,
						diamCircle / 2);
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
		// IJ.log("il massimo del ghost di destra si trova a x= " + xGhMaxDx
		// + " y= " + yGhMaxDx + " mean= " + ghMaxDx);
		//
		// IJ.log("ghost inferiore");

		int critic_3;
		double ghMaxDw = -99999;
		int xGhMaxDw = -9999;
		int yGhMaxDw = -9999;

		for (int i1 = 0; i1 < diamCircle - diamGhost; i1++) {
			for (int i2 = height - diamGhost; i2 > diamCircle - diamGhost; i2--) {
				int py = i2;
				int px = i1 + (xCenterCircle - diamCircle / 2);
				int xcentGhost = px + diamGhost / 2;
				int ycentGhost = py + diamGhost / 2;
				critic_3 = criticalDistanceCalculation(xcentGhost, ycentGhost,
						diamGhost / 2, xCenterCircle, yCenterCircle,
						diamCircle / 2);
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

			// IJ.log("il massimo del ghost inferiore si trova a x= " + xGhMaxDw
			// + " y= " + yGhMaxDw + " mean= " + ghMaxDw);
			MyLog.waitHere(listaMessaggi(23), debug);
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

	public static int[] positionSearch14(ImagePlus imp1, int[] circleData,
			int diamGhost, int guard, String info1, boolean autoCalled,
			boolean step, boolean demo, boolean test, boolean fast,
			boolean irraggiungibile) {

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

		int xCenterCircle = circleData[0];
		int yCenterCircle = circleData[1];
		int diamCircle = circleData[2];

		// disegno il perimetro del fantoccio
		int xRoi0 = xCenterCircle - diamCircle / 2;
		int yRoi0 = yCenterCircle - diamCircle / 2;
		int diamRoi0 = diamCircle;

		if (demo) {
			UtilAyv.showImageMaximized(imp2);
			// MyLog.waitHere(listaMessaggi(30), debug);
		}

		imp2.setRoi(new OvalRoi(xRoi0, yRoi0, diamRoi0, diamRoi0));
		if (demo) {
			imp2.getRoi().setStrokeColor(Color.red);
			over2.addElement(imp2.getRoi());
			MyLog.waitHere(listaMessaggi(30), debug);
		}

		// marco con un punto il centro del fantoccio
		// if (demo) {
		// drawCenter(imp2, over2, xCenterCircle, yCenterCircle, Color.red);
		// MyLog.waitHere(listaMessaggi(32), debug);
		// }

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
			critic_0 = criticalDistanceCalculation(xcentGhost, ycentGhost,
					diamGhost / 2, xCenterCircle, yCenterCircle, diamCircle / 2);
			imp2.setRoi(new OvalRoi(px, py, diamGhost, diamGhost));
			// ghMean = imp2.getStatistics().mean;

			pieno = verifyCircularRoiPixels(imp2, xcentGhost, ycentGhost,
					diamGhost, test, demo);

			if (critic_0 < guard) {
				MyLog.waitHere("Ricerca posizione fondo fallita");
				return null;
			}
		} while (pieno || a > 0);
		if (demo) {
			MyLog.waitHere(listaMessaggi(33), debug);
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
		ImageProcessor ip1 = imp1.getProcessor();

		imp1.setRoi(new OvalRoi(xRoi0, yRoi0, diamRoi0, diamRoi0));
		Rectangle r1 = ip1.getRoi();
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
			IJ.setMinAndMax(imp1, 10, 30);
			UtilAyv.showImageMaximized2(imp1);
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
							// MyLog.waitHere("offset= "+offset+" pixel= "+pixels[vet[i4]]);
							if (demo) {
								imp1.setRoi(x1 + i1, y1 + i2, 1, 1);
								over2.addElement(imp1.getRoi());
							}
						}
					}
					// MyLog.waitHere("sum= " + sum);

					if (sum == 0)
						return true;
				}
			}
		}
		// imp1.updateAndDraw();
		// MyLog.waitHere();
		return false;
	}

	/**
	 * Calcolo delle distanza minima tra due circonferenze esterne
	 * 
	 * @param x1
	 *            coordinata x cerchio 1
	 * @param y1
	 *            coordinata y cerchio 1
	 * @param r1
	 *            raggio cerchio 1
	 * @param x2
	 *            coordinata x cerchio 2
	 * @param y2
	 *            coordinata y cerchio 2
	 * @param r2
	 *            raggio cerchio 2
	 * @return distanza minima tra i cechi
	 */
	public static int criticalDistanceCalculation(int x1, int y1, int r1,
			int x2, int y2, int r2) {

		double dCentri = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1)
				* (y2 - y1));
		double critical = dCentri - (r1 + r2);
		return (int) Math.round(critical);
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

	// /////
	// /////
	// ora mi trovo un "cerchio" di cui determinare il centro. Adotto
	// l'algoritmo adottato da Marco Bettiol: fare una scansione riga per
	// riga dell'immagine e vedere dove si ha la larghezza massima. In
	// questo punto si troverà il centro. La stessa procedura si effettua
	// anche per il verticale. Si dovrebbero identificare 4 punti sulla
	// circonferenza. Questi 4 punti si possono utilizzare per determinare
	// il centro

	// ////

	public static void horizontalScan(ImagePlus imp1, boolean step) {

		// ImagePlus imp2 = imp1.duplicate();
		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));

		ImageConverter ic1 = new ImageConverter(imp1);
		ic1.convertToGray8();
		imp1.updateImage();

		ImageProcessor ip2 = imp1.getProcessor();

		Overlay over1 = new Overlay();

		double[][] peaks1 = new double[4][1];

		// imageProcessor.getWidth dà la larghezza in pixels
		double[][] profi3 = new double[ip2.getWidth()][2];
		double[][] profi4 = new double[ip2.getWidth()][2];

		// short[] pixels = (short[]) ip2.getPixels();
		byte[] pixels = (byte[]) ip2.getPixels();

		double[] pixels2 = new double[pixels.length];
		for (int i1 = 0; i1 < pixels.length; i1++) {
			pixels2[i1] = (double) pixels[i1];
			// IJ.log("" + i1 + "   " + pixels[i1] + "  " + pixels2[i1]);
		}
		// MyLog.waitHere("intera immagine");

		// ip2.setSnapshotCopyMode(true);
		ip2.smooth();
		// ip2.setSnapshotCopyMode(false);

		// if (step)
		// new WaitForUserDialog("Eseguito SMOOTH").show();
		// IJ.run(imp12, "Find Edges", "");
		ip2.findEdges();

		// pixels = (short[]) ip2.getPixels();
		pixels = (byte[]) ip2.getPixels();
		double[] pixels3 = new double[pixels.length];

		for (int i3 = 0; i3 < pixels.length; i3++) {
			pixels3[i3] = (double) pixels[i3];
			// IJ.log("" + i3 + "   " + pixels[i3] + "  " + pixels3[i3]);
		}

		// MyLog.waitHere("intera immagine + smooth + erf");

		double posi;

		double[] profiY = new double[ip2.getWidth()];
		double[] vetLeftX = new double[ip2.getWidth()];
		double[] vetLeftY = new double[ip2.getWidth()];
		double[] vetRightX = new double[ip2.getWidth()];
		double[] vetRightY = new double[ip2.getWidth()];

		for (int i1 = 0; i1 < ip2.getHeight(); i1++) {

			posi = 0.;
			for (int i2 = 0; i2 < ip2.getWidth(); i2++) {
				profi3[i2][0] = posi += dimPixel;
				profi3[i2][1] = pixels2[i1 * ip2.getWidth() + i2];
				// if (i1 == 50) {
				//
				// IJ.log("" + i2 + "  puntatore="
				// + (i1 * ip2.getWidth() + i2) + "    "
				// + profi3[i2][1]);
				// }
			}

			// faccio un plot

			if (i1 == 1200) {

				MyLog.logMatrix(profi3, "profi3");

				String title = "";
				Plot plot2 = MyPlot.basePlot(profi3, title, Color.GREEN);
				plot2.draw();
				plot2.setColor(Color.red);
				// plot2.addPoints(xPoints, yPoints, PlotWindow.CIRCLE);
				plot2.show();
				new WaitForUserDialog("profilo iniziale").show();
			}

			for (int i2 = 0; i2 < ip2.getWidth(); i2++) {
				profiY[i2] = pixels3[i1 * ip2.getWidth() + i2];
			}
			double[] smoothProfi1 = smooth3(profiY, 3);
			double[] smoothProfi = smooth3(smoothProfi1, 3);

			posi = 0.;
			for (int i2 = 0; i2 < ip2.getWidth(); i2++) {
				// profi3[i2][0] = posi += dimPixel;
				profi3[i2][0] = i2;
				profi3[i2][1] = smoothProfi[i2];
			}

			// faccio un plot

			if (i1 == 1200) {
				String title = "";
				Plot plot2 = MyPlot.basePlot(profi3, title, Color.GREEN);
				plot2.draw();
				plot2.setColor(Color.red);
				// plot2.addPoints(xPoints, yPoints, PlotWindow.CIRCLE);
				plot2.show();
				new WaitForUserDialog("profilo dopo smooth").show();
			}

			// MyLog.logMatrix(profi3, "profi3");

			ArrayList<ArrayList<Double>> matOut = peakDet(profi3, 100.);
			double[][] peaks3 = new InputOutput()
					.fromArrayListToDoubleTable(matOut);

			if (peaks3[0].length == 0)
				continue;

			// if (peaks3[0].length == 0)
			// MyLog.waitHere("ERRORE");

			double[] xPoints = new double[peaks3[2].length];
			double[] yPoints = new double[peaks3[2].length];
			for (int i2 = 0; i2 < peaks3[2].length; i2++) {
				xPoints[i2] = peaks3[2][i2];
				yPoints[i2] = peaks3[3][i2];

			}

			if (peaks3[2].length < 2 || peaks3[3].length < 2)
				continue;
			vetLeftX[i1] = peaks3[2][0];
			vetLeftY[i1] = i1;
			// vetLeftY[i1] = peaks3[3][0];
			vetRightX[i1] = peaks3[2][1];
			// vetRightY[i1] = peaks3[3][1];
			vetRightY[i1] = i1;

			if (i1 == 1200) {
				MyLog.logVector(xPoints, "xPoints");
				MyLog.logVector(yPoints, "yPoints");
				String title = "";
				Plot plot2 = MyPlot.basePlot(profi3, title, Color.GREEN);
				plot2.draw();
				plot2.setColor(Color.red);
				plot2.addPoints(xPoints, yPoints, PlotWindow.CIRCLE);
				plot2.show();
				new WaitForUserDialog("posizione picchi").show();
			}

		}
		MyLog.logVector(vetLeftX, "vetLeftX");
		MyLog.logVector(vetLeftY, "vetLeftY");
		MyLog.logVector(vetRightX, "vetRightX");
		MyLog.logVector(vetRightY, "vetRightY");
		int count = 0;

		for (int i1 = 0; i1 < vetLeftX.length; i1++) {
			if ((vetLeftX[i1] != 0) && (vetLeftY[i1] != 0))
				count++;
		}
		for (int i1 = 0; i1 < vetRightX.length; i1++) {
			if ((vetRightX[i1] != 0) && (vetRightY[i1] != 0))
				count++;
		}
		double[] profileX = new double[count];
		double[] profileY = new double[count];

		int pos1 = 0;
		for (int i1 = 0; i1 < vetLeftX.length; i1++) {
			if ((vetLeftX[i1] != 0) && (vetLeftY[i1] != 0)) {
				profileX[pos1] = vetLeftX[i1];
				profileY[pos1] = vetLeftY[i1];
				pos1++;
			}
		}

		// for (int i1 =0; i1 <vetRightX.length; i1++) {
		// if ((vetRightX[i1] != 0) && (vetRightY[i1] != 0)) {
		// profileX[pos1] = vetRightX[i1];
		// profileY[pos1] = vetRightY[i1];
		// pos1++;
		// }
		// }

		for (int i1 = vetRightX.length - 1; i1 >= 0; i1--) {
			if ((vetRightX[i1] != 0) && (vetRightY[i1] != 0)) {
				profileX[pos1] = vetRightX[i1];
				profileY[pos1] = vetRightY[i1];
				pos1++;
			}
		}
		MyLog.logVector(profileX, "profileX");
		MyLog.logVector(profileY, "profileY");

		FloatPolygon fp1 = new FloatPolygon(Tools.toFloat(profileX),
				Tools.toFloat(profileY));
		PolygonRoi pr1 = new PolygonRoi(fp1, Roi.POLYGON);
		imp1.setRoi(pr1);

		imp1.updateAndDraw();
		MyLog.waitHere();

		over1.addElement(imp1.getRoi());
		imp1.setOverlay(over1);
		over1.setStrokeColor(Color.red);

	}

	/***
	 * Mappare i pixel come interni/esterni all'oggetto (oggetti?) prima
	 * passata: per righe e per colonne seconda passata: per righe e per colonne
	 * ma eseguita al contrario (i1--) criterio per considerare un pixel all'
	 * interno: 1) nella scansione per colonne partendo da "fuori" supera il
	 * limite superiore ed è a contatto con almeno 1 pixel "dentro" (per evitare
	 * transienti) 2) un pixel è "dentro" se nella scansione per colonne è a
	 * contatto con almeno un pixel "dentro" e non scende sotto il limite
	 * inferiore. 3) un pixel è fuori se scende sotto il limite inferiore 4) i
	 * limiti inferiore e superiore vanno determinati (forse) in base al rumore
	 * di fondo la marcatura dei pixel come "dentro" o fuori va riconfermata
	 * alla seconda passata
	 * 
	 * @param imp1
	 *            immagine da analizzare
	 * @param lowThreshold
	 *            limite inferiore
	 * @param highThreshold
	 *            limite superiore
	 * @param step
	 *            boolean per tests
	 * @return
	 */
	public static ImagePlus insideOutside(ImagePlus imp1, int lowThreshold,
			int highThreshold, boolean step) {

		ImageProcessor ip1 = imp1.getProcessor();
		short[] pixels1 = (short[]) ip1.getPixels();
		int width = imp1.getWidth();
		int height = imp1.getHeight();
		ImagePlus imp2 = NewImage.createByteImage("AREA", width, height, 1,
				NewImage.FILL_BLACK);

		ImageProcessor ip2 = imp2.getProcessor();
		byte[] pixels2 = (byte[]) ip2.getPixels();
		for (int i1 = 0; i1 < pixels2.length; i1++) {
			pixels2[i1] = (byte) 128; // impongo un valore
										// "neutro ai pixels non analizzati"
		}

		Overlay over1 = new Overlay();
		imp1.setOverlay(over1);
		int dRoi = 10;
		int xRoi;
		int yRoi;
		int posx;
		int posy;
		// misura del fondo
		posx = 0;
		posy = 0;
		xRoi = posx + dRoi * 2 / 3;
		yRoi = posy + dRoi * 2 / 3;
		imp1.setRoi(new OvalRoi(xRoi, yRoi, dRoi, dRoi));
		over1.addElement(imp1.getRoi());
		ImageStatistics stat1 = imp1.getStatistics();
		double med1 = stat1.mean;
		double ds1 = stat1.stdDev;
		posx = width;
		posy = 0;
		xRoi = posx - dRoi - dRoi * 2 / 3;
		yRoi = posy + dRoi * 2 / 3;
		imp1.setRoi(new OvalRoi(xRoi, yRoi, dRoi, dRoi));
		over1.addElement(imp1.getRoi());
		ImageStatistics stat2 = imp1.getStatistics();
		double med2 = stat2.mean;
		double ds2 = stat2.stdDev;
		posx = width;
		posy = height;
		xRoi = posx - dRoi - dRoi * 2 / 3;
		yRoi = posy - dRoi - dRoi * 2 / 3;
		imp1.setRoi(new OvalRoi(xRoi, yRoi, dRoi, dRoi));
		over1.addElement(imp1.getRoi());
		ImageStatistics stat3 = imp1.getStatistics();
		double med3 = stat3.mean;
		double ds3 = stat3.stdDev;
		posx = 0;
		posy = height;
		xRoi = posx + dRoi * 2 / 3;
		yRoi = posy - dRoi - dRoi * 2 / 3;
		imp1.setRoi(new OvalRoi(xRoi, yRoi, dRoi, dRoi));
		over1.addElement(imp1.getRoi());
		over1.setStrokeColor(Color.red);
		ImageStatistics stat4 = imp1.getStatistics();
		double med4 = stat4.mean;
		double ds4 = stat4.stdDev;
		lowThreshold = (int) (((med1 + med2 + med3 + med4) / 4.0) + ((ds1 + ds2
				+ ds3 + ds4) / 4.0));
		highThreshold = (int) ((3 * (med1 + med2 + med3 + med4) / 4.0) + (4 * (ds1
				+ ds2 + ds3 + ds4) / 4.0));
		MyLog.waitHere("lowThreshold= " + lowThreshold + " highThreshold= "
				+ highThreshold);

		// prima scansione
		short val1 = 0;
		for (int i1 = 0; i1 < height; i1++) {
			for (int i2 = 0; i2 < width; i2++) {
				val1 = pixels1[i1 * width + i2];
				if (val1 > highThreshold)
					pixels2[i1 * width + i2] = (byte) 255;
				else if (val1 < lowThreshold)
					pixels2[i1 * width + i2] = (byte) 0;
			}
		}

		// a questo punto dovrei avere a 128 solo i pixel "incerti", è il
		// momento buono per rinfrescare imp2 con pixels2 e mostrare la cazz...
		// pardon nuova immagine
		imp2.updateAndDraw();

		return imp2;

	}

	/***
	 * Mappare i pixel come interni/esterni all'oggetto (oggetti?) prima
	 * passata: per righe e per colonne seconda passata: per righe e per colonne
	 * ma eseguita al contrario (i1--) criterio per considerare un pixel all'
	 * interno: 1) nella scansione per colonne partendo da "fuori" supera il
	 * limite superiore ed è a contatto con almeno 1 pixel "dentro" (per evitare
	 * transienti) 2) un pixel è "dentro" se nella scansione per colonne è a
	 * contatto con almeno un pixel "dentro" e non scende sotto il limite
	 * inferiore. 3) un pixel è fuori se scende sotto il limite inferiore 4) i
	 * limiti inferiore e superiore vanno determinati (forse) in base al rumore
	 * di fondo la marcatura dei pixel come "dentro" o fuori va riconfermata
	 * alla seconda passata
	 * 
	 * @param imp1
	 *            immagine da analizzare
	 * @param lowThreshold
	 *            limite inferiore
	 * @param highThreshold
	 *            limite superiore
	 * @param step
	 *            boolean per tests
	 * @return
	 */
	public static ImagePlus canny(ImagePlus imp1, float lowThreshold,
			float highThreshold, float gaussianKernelRadius,
			boolean contrastNormalized) {

		MyCannyEdgeDetector mce1 = new MyCannyEdgeDetector();
		mce1.setLowThreshold(lowThreshold);
		mce1.setHighThreshold(highThreshold);
		mce1.setGaussianKernelRadius(gaussianKernelRadius);
		mce1.setContrastNormalized(contrastNormalized);
		ImagePlus imp2 = mce1.process(imp1);

		for (int i1 = 0; i1 < imp2.getWidth(); i1++) {

		}

		return imp2;
	}

	private static void msgMainRoiPositioning() {
		ButtonMessages.ModelessMsg(
				"Posizionare ROI diamFantoccio e premere CONTINUA", "CONTINUA");
	}

	private static void msgRoi85percPositioning() {
		ButtonMessages.ModelessMsg(
				"Puoi modificare la posizione ROI con area 80%", "CONTINUA");
	}

	private static void msg85percData(boolean step, double mean1) {
		if (step)
			ButtonMessages.ModelessMsg("media roi 85%=" + mean1, "CONTINUA");
	}

	private static void msgElabImaDiff(boolean step) {
		if (step)
			ButtonMessages
					.ModelessMsg(
							"Elaborata immagine differenza                                                                                        <11>",
							"CONTINUA");
	}

	private static void msgImaDiffData(boolean step, double meanImaDiff) {
		if (step)
			ButtonMessages.ModelessMsg(" mediaImaDiff=" + meanImaDiff + "  ",
					"CONTINUA", "CHIUDI");
	}

	private static void msgImaSimulata(boolean step) {
		if (step)
			ButtonMessages.ModelessMsg("Immagine Simulata", "CONTINUA");
	}

	private static void msgSnRatio(boolean step, double uiPerc1, double snRatio) {
		if (step)
			ButtonMessages.ModelessMsg("Uniformità integrale=" + uiPerc1
					+ "  Rapporto segnale/rumore sn2=" + snRatio, "CONTINUA");
	}

	private static void msgGhostRoi(int count) {
		ButtonMessages.ModelessMsg("Posiziona  la ROI ghost" + count
				+ " (ctrl+shift+E=ridisegna)", "CONTINUA");
	}

	public static void msgSignalGhostRoi(boolean step, double mean) {
		if (step)
			ButtonMessages.ModelessMsg("Segnale medio =" + mean, "CONTINUA");
	}

	private static void msgMoveGhostRoi() {
		ButtonMessages
				.ModalMsg(
						"ATTENZIONE la posizione scelta per il ghost dà segnale medio =0 SPOSTARLO",
						"CONTINUA");
	}

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
			UtilAyv.backgroundEnhancement(0, 0, 10, impSimulata);
		}
		// UtilAyv.autoAdjust(impSimulata, impSimulata.getProcessor());
		impSimulata.updateAndDraw();
		// msgImaSimulata(step);
		// MyLog.waitHere();
		int[][] classiSimulata = numeroPixelsClassi(impSimulata);
		String patName = ReadDicom.readDicomParameter(imp,
				MyConst.DICOM_PATIENT_NAME);

		String codice1 = ReadDicom.readDicomParameter(imp,
				MyConst.DICOM_SERIES_DESCRIPTION);

		String codice = UtilAyv.getFiveLetters(codice1);

		// if (codice1.length() >= 4) {
		// codice = ReadDicom
		// .readDicomParameter(imp, MyConst.DICOM_SERIES_DESCRIPTION)
		// .substring(0, 4).trim();
		// } else {
		// codice = "____";
		// }

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
		lista[8] = "Si determina ora l'eventuale dimensione delle bolla d'aria";
		lista[9] = "Viene mostrata la circonferenza con area 80% del fantoccio, chiamata MROI";
		lista[10] = "Il centro della MROI è contrassegnato dal pallino verde";
		lista[11] = "11";
		lista[12] = "12";
		lista[13] = "13";
		lista[14] = "14";
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
		lista[24] = "24";
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
				+ "nell'area uguale a MROI, evidenziata in verde, si indica con SD la \n"
				+ "Deviazione Standard di questa area SD1 = ";
		lista[43] = "Utilizzando la media del segnale sulla roi evidenziata in rosso \n"
				+ "sulla prima immagine e la deviazione standard della roi evidenziata \n"
				+ "in verde sulla immagine differenza, si calcola il rapporto Segnale/Rumore\n \n"
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
		// ---------+-----------------------------------------------------------+
		lista[50] = "Per valutare i ghosts viene calcolata la statistica per ognuna delle 4 ROI, \n"
				+ "lo stesso per il fondo  \n \n  ghostPerc = ((mediaGhost - mediaFondo) / S1) * 100.0\n \n";

		String out = lista[select];
		return out;
	}

	public static void plotPoints(ImagePlus imp1, Overlay over1,
			double[][] peaks1) {

		float[] xPoints = new float[peaks1[0].length];
		float[] yPoints = new float[peaks1[0].length];

		// xPoints[0] = (float) peaks1[2][0];
		// yPoints[0] = (float) peaks1[3][0];
		// xPoints[1] = (float) peaks1[2][1];
		// yPoints[1] = (float) peaks1[3][1];

		for (int i1 = 0; i1 < peaks1[0].length; i1++) {
			xPoints[i1] = (float) peaks1[2][i1];
			yPoints[i1] = (float) peaks1[3][i1];
		}

		imp1.setRoi(new PointRoi(xPoints, yPoints, xPoints.length));
		imp1.getRoi().setStrokeColor(Color.green);
		over1.addElement(imp1.getRoi());
	}

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

	public static void drawCenter(ImagePlus imp1, Overlay over1,
			int xCenterCircle, int yCenterCircle, Color color1) {
		// imp1.setOverlay(over1);
		imp1.setRoi(new OvalRoi(xCenterCircle - 2, yCenterCircle - 2, 4, 4));
		Roi roi1 = imp1.getRoi();
		roi1.setFillColor(color1);
		roi1.setStrokeColor(color1);
		over1.addElement(imp1.getRoi());
		imp1.killRoi();
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
