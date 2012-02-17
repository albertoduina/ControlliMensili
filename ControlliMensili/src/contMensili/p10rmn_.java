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
	private static boolean pulse = false; // lasciare, serve anche se segnalato
											// inutilizzato

	public void run(String args) {

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
	 * A QUESTA DESCRIZIONE VA AGGIUNTO IL MODO DI FUNZIONAMENTO SE ANCHE UNO
	 * SOLO DEI PARAMETRI VA AL DI FUORI DAI LIMITI DI MASSIMA:
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
				double profond = 30;
				mainUnifor(path1, path2, "0", profond, "", autoCalled, step,
						verbose, test, fast);

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
			Msg.msgParamError();
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
		if (fast) {
			retry = false;
			boolean autoCalled = true;
			boolean verbose = false;
			boolean test = false;

			double profond = readDouble(TableSequence.getProfond(iw2ayvTable,
					vetRiga[0]));
			if (UtilAyv.isNaN(profond)) {
				MyLog.logVector(iw2ayvTable[vetRiga[0]], "stringa");
				MyLog.waitHere();
			}

			mainUnifor(path1, path2, autoArgs, profond, info10, autoCalled,
					step, verbose, test, fast);

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

					double profond = Double.parseDouble(TableSequence
							.getProfond(iw2ayvTable, vetRiga[0]));

					mainUnifor(path1, path2, autoArgs, profond, info10,
							autoCalled, step, verbose, test, fast);

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
			boolean step, boolean verbose, boolean test, boolean fast) {

		boolean accetta = false;
		boolean slow = false;
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		double angle = Double.NaN;
		String[][] limiti = new InputOutput().readFile6("LIMITI.csv");
		String[] vetMinimi = p10rmn_.decoderLimiti(limiti, "P10MIN");
		String[] vetMaximi = p10rmn_.decoderLimiti(limiti, "P10MAX");
		MyLog.logVector(vetMinimi, "vetMinimi");		
		MyLog.logVector(vetMaximi, "vetMaximi");		
		
		
		MyLog.waitHere("sono appena prima del do");
		do {

			MyLog.waitHere("sono appena dopo il do fast=" + fast + " verbose="
					+ verbose);
			ImagePlus imp11 = null;
			if (fast)
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

			// ========================================================================
			// se non ho trovato la posizione mi ritrovo qui senza out2[]
			// valido
			//
			// se anche uno solo dei check limits è fallito si deve tornare qui
			// ed eseguire il controllo, come minimo senza fast attivo ed in
			// modalità verbose
			//
			//
			// ========================================================================
			ImagePlus imp1 = null;
			ImagePlus imp2 = null;
			if (verbose) {
				imp1 = UtilAyv.openImageMaximized(path1);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			} else {
				imp1 = UtilAyv.openImageNoDisplay(path1, true);
				imp2 = UtilAyv.openImageNoDisplay(path2, true);
			}
			if (imp2 == null)
				MyLog.waitHere("Non trovato il file " + path2);
			// ImageWindow iw1=WindowManager.getCurrentWindow();

			angle = out2[6];
			// IJ.log("angle= " + angle);
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

			int width = imp1.getWidth();
			int height = imp1.getHeight();
			double dimPixel = ReadDicom.readDouble(ReadDicom.readSubstring(
					ReadDicom.readDicomParameter(imp1,
							MyConst.DICOM_PIXEL_SPACING), 1));

			if (verbose) {
				// =================================================
				imp1.setRoi(xCenterRoi - 10, yCenterRoi - 10, 20, 20);
				over2.addElement(imp1.getRoi());
				imp1.killRoi();
				imp1.setRoi(new OvalRoi(xCenterCircle - 4, yCenterCircle - 4,
						8, 8));
				over2.addElement(imp1.getRoi());
				imp1.killRoi();
				imp1.setRoi(new OvalRoi(xMaxima - 4, yMaxima - 4, 8, 8));
				over2.addElement(imp1.getRoi());
				imp1.setRoi(new Line(xCenterCircle, yCenterCircle, xMaxima,
						yMaxima));
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.green);
				imp1.updateAndDraw();
				// =================================================
			}

			imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA,
					sqNEA);
			if (verbose) {
				over2.addElement(imp1.getRoi());
			}
			imp1.updateAndDraw();
			if (step)
				new WaitForUserDialog("MROI 11 x 11 Premere  OK").show();
			//
			// posiziono la ROI 7x7 all'interno di MROI
			//
			int sq7 = MyConst.P10_MROI_7X7_PIXEL;
			imp1.setRoi(xCenterRoi - sq7 / 2, yCenterRoi - sq7 / 2, sq7, sq7);
			if (verbose) {
				over2.addElement(imp1.getRoi());
				over2.setStrokeColor(Color.green);
			}
			imp1.updateAndDraw();
			if (step)
				new WaitForUserDialog("MROI 7 x 7 Premere  OK").show();

			ImageStatistics stat7x7 = imp1.getStatistics();

			// eseguo un controllo di sicurezza sul risultato.
			UtilAyv.checkLimits(stat7x7.mean, 0, 3000, "stat7x7.mean");

			int xFondo = MyConst.P10_X_ROI_BACKGROUND;
			int yFondo = MyConst.P10_Y_ROI_BACKGROUND;
			int dFondo = MyConst.P10_DIAM_ROI_BACKGROUND;
			if (step)
				msgMroi();
			//
			// disegno RoiFondo su imp1
			//
			Boolean circular = true;
			ImageStatistics statFondo = UtilAyv.backCalc2(xFondo, yFondo,
					dFondo, imp1, step, circular, test);

			// UtilAyv.checkLimits(statFondo.mean, 0, 50, "statFondo.mean");
			UtilAyv.checkLimits(statFondo.mean, 0, 50, "statFondo.mean");

			//
			// disegno MROI su imaDiff
			//
			ImagePlus imaDiff = UtilAyv.genImaDifference(imp1, imp2);
			if (verbose && !fast)
				UtilAyv.showImageMaximized(imaDiff);
			imaDiff.setOverlay(over3);
			over3.setStrokeColor(Color.green);

			if (true) {
				// =================================================
				imaDiff.setRoi(xCenterRoi - 10, yCenterRoi - 10, 20, 20);
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

			UtilAyv.checkLimits(statImaDiff.stdDev, 0, 20, "statImaDiff.stdDev");

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
			imp1.setRoi(xCenterRoi - sqNEA / 2, yCenterRoi - sqNEA / 2, sqNEA,
					sqNEA);
			imp1.updateAndDraw();
			if (imp1.isVisible())
				imp1.getWindow().toFront();

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
				if (step && pixx >= area11x11)
					msgSqr2OK(pixx);

			} while (pixx < area11x11);
			// MyLog.waitHere();

			if (imp1.isVisible())
				imp1.getWindow().toFront();

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
				msgDisplayMean4(out11[0], out11[1]);
			//
			// calcolo SNR finale
			//
			double snr = stat7x7.mean / (out11[1] / Math.sqrt(2));
			if (step)
				msgSnr(snr);

			int userSelection2 = UtilAyv.checkLimits(snr, 1, -10, "snr");
			if (userSelection2 == 2) {
				fast = false;
				verbose = true;
				continue;
			}
			if (userSelection2 == 3) {
				break;
			}

			String patName = ReadDicom.readDicomParameter(imp1,
					MyConst.DICOM_PATIENT_NAME);
			String codice = ReadDicom
					.readDicomParameter(imp1, MyConst.DICOM_SERIES_DESCRIPTION)
					.substring(0, 4).trim();

			simulataName = fileDir + patName + codice + "sim.zip";

			int[][] classiSimulata = ImageUtils.generaSimulata12classi(
					xCenterRoi, yCenterRoi, sq7, imp1, simulataName, step,
					false, test);

			// if (imp1.isVisible())
			// imp1.getWindow().toFront();

			//
			// calcolo posizione fwhm a metà della MROI
			//
			if (imp1.isVisible())
				imp1.getWindow().toFront();
			//
			// ----------------------------------------------------------
			// Calcolo FWHM
			// la direzione su cui verrà preso il profilo è quella centro
			// cerchio - centro roi, il segmento raggiunge i bordi
			// -----------------------------------------------------------
			//

			double[] out3 = crossing(xCenterCircle, yCenterCircle, xCenterRoi,
					yCenterRoi, width, height);

			// aveva restituito null
			if (out3 == null)
				MyLog.waitHere("out3==null");

			int xStartProfile = (int) Math.round(out3[0]);
			int yStartProfile = (int) Math.round(out3[1]);
			int xEndProfile = (int) Math.round(out3[2]);
			int yEndProfile = (int) Math.round(out3[3]);

			imp1.setRoi(new Line(xStartProfile, yStartProfile, xEndProfile,
					yEndProfile));
			imp1.updateAndDraw();

			if (imp1.isVisible())
				imp1.getWindow().toFront();
			// MyLog.waitHere();

			step = false;
			double[] outFwhm2 = analyzeProfile3(imp1, xStartProfile,
					yStartProfile, xEndProfile, yEndProfile, dimPixel, step);
			//
			// ricordo che qui sotto la lunghezza minima che accetto vale 5, la
			// massima sarà la lunghezza totale del profilo, calcolata da ImageJ
			// (si potrebbe scegliere altro, ma questa dovrebbe andare bene

			// // UtilAyv.checkLimits(outFwhm2[0], 5, outFwhm2[2],
			// "outFwhm2[0]");
			UtilAyv.checkLimits(outFwhm2[0], 1, outFwhm2[2], "outFwhm2[0]");

			//
			// Salvataggio dei risultati nella ResultsTable
			//
			String[][] tabCodici = TableCode.loadTable(MyConst.CODE_FILE);

			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1,
					imp1, tabCodici, VERSION, autoCalled);
			// MyLog.waitHere();

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
			rt.addValue(2, statFondo.mean);
			rt.addValue(3, statFondo.roiX);
			rt.addValue(4, statFondo.roiY);
			rt.addValue(5, statFondo.roiWidth);
			rt.addValue(6, statFondo.roiHeight);

			rt.incrementCounter();
			rt.addLabel(t1, "SnR");
			rt.addValue(2, snr);
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
				break;

			case 2:
				// Siemens
				verbose = true;
				ok = selfTestSiemens(verbose);
				if (ok)
					Msg.msgTestPassed();
				else
					Msg.msgTestFault();
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
		boolean test = true;
		double profond = 30;
		boolean fast = false;

		ResultsTable rt1 = mainUnifor(path1, path2, autoArgs, profond, "",
				autoCalled, step, verbose, test, fast);
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
		double backNoise = 12.225;
		double snRatio = 35.48765967441802;
		double fwhm = 11.401143711292473;
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
		double backNoise = 12.225;
		double snRatio = 35.48765967441802;
		double fwhm = 11.401143711292473;
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

		if (imp1 == null) {
			IJ.error("CountPixTest ricevuto null");
			return (0);
		}

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

	private static double[] analyzeProfile3(ImagePlus imp1, int ax, int ay,
			int bx, int by, double dimPixel, boolean step) {

		if (imp1 == null) {
			IJ.error("analyzeProfile3  ricevuto null");
			return (null);
		}

		imp1.setRoi(new Line((int) ax, (int) ay, (int) bx, (int) by));
		Roi roi1 = imp1.getRoi();

		double[] profi1 = ((Line) roi1).getPixels(); // profilo non mediato

		double length = roi1.getLength();

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
		outFwhm = calcFwhm(vetHalfPoint, profi1, dimPixel, length);

		if (step)

			createPlot(profi1, true, true); // plot della fwhm
		if (step)
			ButtonMessages.ModelessMsg("Continuare?   <51>", "CONTINUA");
		return (outFwhm);
	} // analProf2

	/**
	 * Calcolo dell'FWHM su di un vettore profilo, valori in mm
	 * 
	 * @param vetUpDwPoints
	 *            Vettore restituito da AnalPlot2 con le posizioni dei punti
	 *            sopra e sotto la metà altezza
	 * @param profile
	 *            Profilo da analizzare
	 * @return out[0]=FWHM, out[1]=peak position
	 */

	private static double[] calcFwhm(int[] vetUpDwPoints, double profile[],
			double dimPixel, double lengthImagej) {

		double peak = 0;
		double[] a = Tools.getMinMax(profile);
		double min = a[0];
		double max = a[1];
		// interpolazione lineare sinistra
		double px0 = vetUpDwPoints[0];
		double px1 = vetUpDwPoints[1];
		double py0 = profile[vetUpDwPoints[0]];
		double py1 = profile[vetUpDwPoints[1]];
		double py2 = (max - min) / 2.0 + min;
		double sx = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		// interpolazione lineare destra
		px0 = vetUpDwPoints[2];
		px1 = vetUpDwPoints[3];
		py0 = profile[vetUpDwPoints[2]];
		py1 = profile[vetUpDwPoints[3]];
		py2 = (max - min) / 2.0 + min;
		double dx = px0 + (px1 - px0) / (py1 - py0) * (py2 - py0);
		// Non conoscendo l'incilnazione del mio profilo,
		// utilizzo la lunghezza del profilo calcolata da ImageJ
		// in pratica ho le seguenti cose:
		//
		// lengthImagej = lunghezza profilo completo, data da Imagej
		//
		// profile.length = lunghezza del profilo, come numero di pixels
		//
		// dx-sx = lunghezza reale in pixels

		double fwhm2 = lengthImagej * (dx - sx) / profile.length;
		for (int i1 = 0; i1 < profile.length; i1++) {
			if (profile[i1] == min)
				peak = i1;
		}
		double[] out = { fwhm2, peak, lengthImagej };
		return (out);
	}

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
	public static double[] interpola(double ax, double ay, double bx,
			double by, double prof) {

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
	 * segmento ed i lati del frame
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

		double ax = 0;
		double ay = 0;
		int xCenterCircle = 0;
		int yCenterCircle = 0;
		double xMaxima = 0;
		double yMaxima = 0;
		double angle12 = 0;
		double xCenterRoi = 0;
		double yCenterRoi = 0;
		Overlay over12 = new Overlay();

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(
						ReadDicom.readDicomParameter(imp11,
								MyConst.DICOM_PIXEL_SPACING), 1));

		int width = imp11.getWidth();
		int height = imp11.getHeight();
		// MyLog.waitHere("step=" + step + " verbose=" + verbose + " test=" +
		// test
		// + " fast=" + fast + " profond=" + profond);

		// if (fast || test) {

		ImagePlus imp12 = imp11.duplicate();
		if (step)
			UtilAyv.showImageMaximized(imp12);

		//
		// -------------------------------------------------
		// Determinazione del cerchio
		// -------------------------------------------------
		//
		IJ.run(imp12, "Smooth", "");
		if (step)
			new WaitForUserDialog("Eseguito SMOOTH").show();
		IJ.run(imp12, "Find Edges", "");
		if (step)
			new WaitForUserDialog("Eseguito FIND EDGES").show();

		imp12.setOverlay(over12);

		double[][] peaks1 = new double[4][1];
		double[][] peaks2 = new double[4][1];
		double[][] peaks3 = new double[4][1];
		double[][] peaks4 = new double[4][1];
		boolean showProfiles = step;

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
		// MyLog.waitHere();
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
			imp12.setRoi(new PointRoi(xPoints3, yPoints3, xPoints3.length));
			if (step) {
				imp12.updateAndDraw();
				over12.addElement(imp12.getRoi());
				over12.setStrokeColor(Color.red);
				imp12.setOverlay(over12);
				imp12.updateAndDraw();
				new WaitForUserDialog("Premere OK").show();
			}
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
		//
		// ----------------------------------------------------------
		// disegno la ROI del maxima, a solo scopo dimostrativo !
		// ----------------------------------------------------------
		//

		// x1 ed y1 sono le due coordinate del punto di maxima
		double[] out10 = UtilAyv.findMaximumPosition(imp12);
		xMaxima = out10[0];
		yMaxima = out10[1];
		if (verbose) {
			imp12.setRoi(new OvalRoi((int) xMaxima - 4, (int) yMaxima - 4, 8, 8));
			over12.addElement(imp12.getRoi());
			over12.setStrokeColor(Color.red);
		}
		imp12.killRoi();
		//
		// -----------------------------------------------------------
		// Calcolo delle effettive coordinate del segmento
		// centro-circonferenza
		// ----------------------------------------------------------
		//
		double xStartRefLine = (double) xCenterCircle;
		double yStartRefLine = (double) yCenterCircle;
		double xEndRefLine = out10[0];
		double yEndRefLine = out10[1];

		imp12.setRoi(new Line(xCenterCircle, yCenterCircle, (int) out10[0],
				(int) out10[1]));
		angle12 = imp12.getRoi().getAngle(xCenterCircle, yCenterCircle,
				(int) out10[0], (int) out10[1]);

		over12.addElement(imp12.getRoi());
		over12.setStrokeColor(Color.red);
		//
		// -----------------------------------------------------------
		// Calcolo coordinate centro della MROI
		// ----------------------------------------------------------
		//

		double[] out1 = interpola(xEndRefLine, yEndRefLine, xStartRefLine,
				yStartRefLine, profond / dimPixel);
		ax = out1[0];
		ay = out1[1];
		imp12.setRoi((int) ax - 10, (int) ay - 10, 20, 20);
		imp12.updateAndDraw();
		over12.addElement(imp12.getRoi());
		over12.setStrokeColor(Color.red);
		//
		// Se non necessito di un intervento manuale, mi limito a leggere le
		// coordinate della ROI determinata in automatico.
		//

		Rectangle boundRec4 = imp12.getProcessor().getRoi();
		xCenterRoi = boundRec4.getCenterX();
		yCenterRoi = boundRec4.getCenterY();
		imp12.hide();
		// }

		if (!fast && !test) {
			UtilAyv.showImageMaximized(imp11);
			imp11.setOverlay(over12);
			imp11.setRoi((int) ax - 10, (int) ay - 10, 20, 20);
			imp11.updateAndDraw();
			MyLog.waitHere(info1 + "\n \nMODIFICA MANUALE POSIZIONE ROI");
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

		double[] out2 = new double[7];
		out2[0] = xCenterRoi;
		out2[1] = yCenterRoi;
		out2[2] = xCenterCircle;
		out2[3] = yCenterCircle;
		out2[4] = xMaxima;
		out2[5] = yMaxima;
		out2[6] = angle12;
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
			MyLog.waitHere("input non numerico= " + s1);
			x = Double.NaN;
		}
		return x;
	}

	public static String[] decoderLimiti(String[][] tableLimiti, String vetName) {
		String[] result;
		for (int i1 = 0; i1 < tableLimiti.length; i1++) {
			if (tableLimiti[i1][0].equals(vetName)) {
				result = tableLimiti[i1];
				return result;
			}
		}
		return null;
	}

}
