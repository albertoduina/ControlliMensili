package contMensili;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.StringTokenizer;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.measure.CurveFitter;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.MyConst;
import utils.MyFileLogger;
import utils.MyLog;
import utils.MyMsg;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableExpand;
import utils.TableSequence;
import utils.UtilAyv;

/*
 * Copyright (C) 2025 Alberto Duina, SPEDALI CIVILI DI BRESCIA, Brescia ITALY
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
 * Analizza DIFFUSIONE
 *
 *
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 *
 *         Tag utilizzati nel plugin (ricevuti da Lorella) Gradiente 0019,100C
 *         Direzioni 0019,100E Questi TAG possono venire letti direttamente in
 *         esadecimale. Da quanto appare la direzione e' scritta come una terna
 *         di valori Double a 64 bit. I valori solitamente presenti sono -1/0/0
 *         oppure 0/-1/0 oppure 0/0/1 ma mi e' anche capitato di trovare *
 *         ATTENZIONE: TESTATO E VERIFICATO I VALORI - DI SOLITO HO TERNE DI
 *         -1/0/0 OPPURE 0/-1/0 OPPURE 0/0/1 MA HO ANCHE TROVATO
 *         -1/-3.9999996e-004/0 QUINDI PUO' SUCCEDERE DI TROVARE VALORI ALLA
 *         CAXXXO una alternativa meno complicata potrebbe essere, come avevo
 *         fatto, di usare l'ultima lettera della 0018,0024 che si chiama
 *         sequenceName
 */

public class p16rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "DIFFUSIONE";

	private static String TYPE = " >> MISURA DIFFUSIONE_____________";

	private static String fileDir = "";
	private static boolean debug = true;
	private static boolean mylogger = true;

	public static String[] scarletta = { "DWL_A", "DWH1A", "DWH2A", "DWH3A", "DWH4A", "DWH5A" };
	public static int[] icarletta = { 10, 6, 6, 6, 6, 6 };
	public static String lastOld = "";
	public static double coeffAngolareDUMMY = 0.002222;
	public static double intercettaDUMMY = -0.001111;
	public static boolean logCOMMENTI = false;

	@Override
	public void run(String args) {

		// UtilAyv.setMyPrecision(); // VAI A VEDERE CHE MI HA FATTO SCLERARE PER NULLA
		// !!!!
		Analyzer.setPrecision(9);

		// -----------------------------
		if (IJ.versionLessThan("1.43k")) {
			return;
		}
		// -----------------------------
		Count c1 = new Count();
		if (!c1.jarCount("iw2ayv_")) {
			return;
		}
		// -----------------------------
		String className = this.getClass().getName();
		String user1 = System.getProperty("user.name");
		// -----------------------------
		TableCode tc1 = new TableCode();
		String iw2ayv1 = tc1.nameTable("codici", "csv");
		String iw2ayv2 = tc1.nameTable("expand", "csv");
		// -----------------------------
		VERSION = user1 + ":" + className + "build_" + MyVersion.getVersion() + ":iw2ayv_build_"
				+ MyVersionUtils.getVersion() + ":" + iw2ayv1 + ":" + iw2ayv2;
		// -----------------------------
		fileDir = Prefs.get("prefer.string1", "none");
		// -----------------------------
		if (mylogger) {
			MyFileLogger.logger.info("p16rmn_>>> fileDir= " + fileDir);
		}
		// -----------------------------
		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}
		return;
	}

	/**
	 * Manual menu, invoked directly from the ImageJ plugins/ContMensili/p16rmn_
	 *
	 * @param preset
	 * @param testDirectory
	 * @return
	 */
	public int manualMenu(int preset, String testDirectory) {
		boolean retry = false;
		do {
			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox().about("Controllo Uniformita'",
				// this.getClass());
				new AboutBox().about("MISURA DIFFUSIONE", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				retry = true;
				break;
			case 4:
			case 5:
				String path1 = UtilAyv.imageSelection("SELEZIONARE PRIMA IMMAGINE...");
				if (path1 == null) {
					return 0;
				}
				String path2 = UtilAyv.imageSelection("SELEZIONARE SECONDA IMMAGINE...");
				if (path2 == null) {
					return 0;
				}
				break;
			}
		} while (retry);
		new AboutBox().close();
		UtilAyv.afterWork();
		return 0;
	}

	/**
	 * ================================================================ Auto menu
	 * invoked from Sequenze_
	 *
	 * @param autoArgs
	 * @return ================================================================
	 */
	public int autoMenu(String autoArgs) {

		// Qui vengono eseguiti i calcoli di diffusione, il resto serve per uniformarsi
		// agli altri plugins

		MyLog.appendLog(fileDir + "MyLog.txt", "p16 riceve " + autoArgs);

		// the autoArgs are passed from Sequenze_
		// possibilities:
		// 1 token -1 = silentAutoTest
		// 2 tokens auto
		// 4 tokens auto
		//

		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);

		String[] path = loadPath(autoArgs);

		boolean retry = false;
		boolean step = false;
		ResultsTable rt = null;
		boolean valid1 = Prefs.getBoolean(".prefer.p16rmn_valid", false);
		int userSelection1 = 0;

		do {
			if (valid1) {
				userSelection1 = 4;
				// se ho gia'posizionato la ROI la utilizzo anche per tutte le immagini
				// successive del paccotto (paccotto=pacchetto multiplo)
			} else {
				// int userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE);
				userSelection1 = UtilAyv.userSelectionAuto(VERSION, TYPE,
						TableSequence.getCode(iw2ayvTable, vetRiga[0]), TableSequence.getCoil(iw2ayvTable, vetRiga[0]),
						vetRiga[0] + 1, TableSequence.getLength(iw2ayvTable));
			}

			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				return 0;
			case 2:
				// new AboutBox().about("Controllo Uniformita'",
				// this.getClass());
				new AboutBox().about("Misura DIFFUSIONE", MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				step = true;
			case 4:
				boolean verbose = true;
				boolean test = false;
				boolean autoCalled = true;
				// MyLog.waitHere("eseguo mainDiffusion");

				rt = mainDiffusion(path, autoArgs, autoCalled, step, verbose, test);

				rt.showRowNumbers(true);
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);
				retry = false;
				break;
			}

		} while (retry);
		new AboutBox().close();
		// UtilAyv.afterWork();
		return 0;
	}

	/***
	 * Main diffusion
	 * 
	 * @param path
	 * @param autoArgs
	 * @param autoCalled
	 * @param step
	 * @param verbose
	 * @param test
	 * @return
	 */
	// @SuppressWarnings("deprecation")
	public static ResultsTable mainDiffusion(String[] path, String autoArgs, boolean autoCalled, boolean step,
			boolean verbose, boolean test) {

		boolean accetta = false;
		boolean valid2 = false;
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);
//		Overlay over1 = new Overlay();
		String path1 = path[0];
//		int count = 0;
//		boolean reference = false;

		//
		// ATTENZIO' ATTENZIO' ATTENZIO'
		// i pacchetti di immagini che ricevo sono solo quelli "ASSIALI", gli altri li
		// faccio cancellare a manina, pero' ricevo 31 immagini, di cui 1 non ha
		// direzione e le restanti 30 hanno direzione r,p,s quindi devo distinguere da
		// me le immagini delle diverse direzioni (RICORDA non credere mai ai
		// canti delle sirene di Ulisse)
		//

		//
		// recupero delle preferenze posizionamento ROI 0,X,Y,Z

		double prefX = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiX", "30"));
		double prefY = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiY", "30"));
		double prefD = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiD", "30"));
		valid2 = Prefs.getBoolean(".prefer.p16rmn_valid", false);

		if (logCOMMENTI) IJ.log(">>>> RESTART lettura iniziale preferenze " + prefX + " " + prefY + " " + prefD);

		double xRoi0 = 9999;
		double yRoi0 = 9999;
		double xRoi2 = 9999;
		double yRoi2 = 9999;
		double rRoi2 = 10; // raggio = 10, diametro = 20
		//
		ArrayList<String> subPathX = new ArrayList<String>();
		ArrayList<String> subPathY = new ArrayList<String>();
		ArrayList<String> subPathZ = new ArrayList<String>();
		String[] pathBB = null;

		ImagePlus imp00 = UtilAyv.openImageNoDisplay(path[0], true);
		//
		// inizio subito a scrivere Results
		//
		TableCode tc11 = new TableCode();
		String[][] tabCodici = tc11.loadMultipleTable("codici", ".csv");

		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp00, tabCodici, VERSION, autoCalled);
		rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);
		rt.showRowNumbers(true);
		String t1 = "TESTO";
		String s2 = "VALORE";
		String s3 = "roi_x";
		String s4 = "roi_y";
		String s5 = "roi_b";
		String s6 = "roi_h";
		//
		// in questo plugin si applica la "teoria della mucca", ovvero: se do da
		// mangiare alla mucca un certo numero di balle di fieno, poi, dal lato opposto
		// della mucca esce il medesimo numero .... e dicono che porti fortuna se lo
		// calpesti .....
		//
		for (int i1 = 0; i1 < path.length; i1++) {
			//
			// a seconda della direzione da elaborare seleziono il path delle immagini ed
			// il preposizionamento della ROI
			//
			ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[i1], true);
			String sName = ReadDicom.readDicomParameter(imp0, MyConst.DICOM_SEQUENCE_NAME);
			int len = sName.length();
			String last = sName.substring(len - 1, len); // leggo ultima lettera del DICOM_SEQUENCE_NAME

			switch (last) {
			case "0":
				subPathX.add(path[i1]);
				subPathY.add(path[i1]);
				subPathZ.add(path[i1]);
				// subPathW.add(path[i1]);
				break;
			case "p":
				// IJ.log("sono nel caso P_X");
				subPathX.add(path[i1]);
				break;
			case "r":
				// IJ.log("sono nel caso R_Y");
				subPathY.add(path[i1]);
				break;
			case "s":
				// IJ.log("sono nel caso S_Z");
				subPathZ.add(path[i1]);
				break;
			default:
				MyLog.waitHere("HOUSTON, ABBIAMO FATTO UN BORDELLO!");
			}
		}
		String[] pathX = ArrayUtils.arrayListToArrayString(subPathX);
		String[] pathY = ArrayUtils.arrayListToArrayString(subPathY);
		String[] pathZ = ArrayUtils.arrayListToArrayString(subPathZ);

		double xRoi1 = 999.9;
		double yRoi1 = 999.9;
		double meanBase = 0;
		double rRoi1 = 0;
		double rRoi0 = 0;

		for (int z1 = 0; z1 < 3; z1++) {

			switch (z1) {
			case 0:
				rt.incrementCounter();
				rt.addValue(t1, "<---- direzione  X ---->");
				pathBB = pathX;
				xRoi0 = prefX;
				yRoi0 = prefY;
				rRoi0 = prefD;
				break;
			case 1:
				rt.incrementCounter();
				rt.addValue(t1, "<---- direzione  Y ---->");
				pathBB = pathY;
				xRoi0 = prefX;
				yRoi0 = prefY;
				rRoi0 = prefD;
				break;
			case 2:
				rt.incrementCounter();
				rt.addValue(t1, "<---- direzione  Z ---->");
				pathBB = pathZ;
				xRoi0 = prefX;
				yRoi0 = prefY;
				rRoi0 = prefD;
				break;
			}
			//
			// start del loop di elaborazione
			//
			do {

				ImagePlus impStack = stackBuilder2(pathBB, true);
				int frames = pathBB.length;

				impStack.setSliceWithoutUpdate(1);

				String name1 = ReadDicom.readDicomParameter(impStack, MyConst.DICOM_SERIES_DESCRIPTION);

				double dimPixel = ReadDicom.readDouble(ReadDicom
						.readSubstring(ReadDicom.readDicomParameter(impStack, MyConst.DICOM_PIXEL_SPACING), 1));

				String dir = name1.substring(4, 5);
				String last = dir.substring(dir.length() - 1);
				boolean chgDir = !last.equals(lastOld);
				lastOld = last;
				// ========================================================
				// predispongo la ROI con diametro fantoccio, in base ad essa viene poi
				// posizionata la roi diametro 20 per i calcoli
				// ========================================================
				ImagePlus imp0 = myImageFromStack(impStack, 1);
				UtilAyv.showImageMaximized(imp0);
				// questa e' la roi verde esterna
				mySetRoi(imp0, xRoi0, yRoi0, rRoi0, null, Color.green);

				if (logCOMMENTI)
					IJ.log("UNO_ROI VERDE_xRoi0= " + xRoi0 + " " + yRoi0 + " " + rRoi0);

				if (!valid2 || chgDir) {
					if (logCOMMENTI)
						IJ.log(">>>> CAMBIO POSIZIONAMENTO ROI VERDE");

					int resp = 0;

					// if (!test) {
					resp = ButtonMessages.ModelessMsg(
							" Posizionare ROI diamFantoccio e premere CONTINUA,  altrimenti, se l'immagine NON E'ACCETTABILE premere ANNULLA per passare alle successive",
							"CONTINUA", "ANNULLA");
					// }

					if (resp == 1) {
						return null;
					}
					valid2 = true;

					// dati posizionamento Roi1
					Rectangle boundingRectangle1 = imp0.getRoi().getBounds();
					double dRoi1 = boundingRectangle1.width;
					rRoi1 = dRoi1 / 2;
					xRoi1 = boundingRectangle1.x;
					yRoi1 = boundingRectangle1.y;

					// ho impostato la Roi0 e modificata con Roi1, disegno la Roi2 in rosso solo a
					// scopo diagnostico
					if (logCOMMENTI)
						IJ.log(">>>> LEGGO NUOVO POSIZIONAMENTO ROI VERDE_xRoi1= " + xRoi1 + " yRoi1= " + yRoi1
								+ " rRoi1= " + rRoi1 * 2);

					// DEVO DECIDERE COME SCRIVERE LE PREFERENZE NEL FILE

					if (logCOMMENTI)
						IJ.log(">>>> TRE salvo preferenze_xRoi1= " + xRoi1 + " roiY= " + yRoi1 + " roiD= " + rRoi1 * 2);

					Prefs.set("prefer.p16rmn_roiX", Double.toString(xRoi1));
					Prefs.set("prefer.p16rmn_roiY", Double.toString(yRoi1));
					Prefs.set("prefer.p16rmn_roiD", Double.toString(rRoi1 * 2));
					Prefs.set("prefer.p16rmn_valid", valid2);
					IJ.wait(20);
					/// EFFETTUO LA RILETTURA, per verifica
					prefX = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiX", "30"));
					prefY = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiY", "30"));
					prefD = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiD", "30"));

					if (!(prefX == xRoi1) && (prefY == yRoi1) && (prefD == rRoi1 * 2))
						MyLog.waitHere("errore salvataggio dati xRoi1");

				}

				prefX = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiX", "30"));
				prefY = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiY", "30"));
				prefD = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiD", "30"));
				if (logCOMMENTI)
					IJ.log(">>>> QUATTRO rileggo preferenze roiX= " + prefX + " roiY= " + prefY + " roiD= " + prefD);

				xRoi0 = prefX;
				yRoi0 = prefY;
				rRoi0 = prefD;

				xRoi2 = xRoi0 + rRoi0 / 2 - rRoi2;
				yRoi2 = yRoi0 + rRoi0 / 2 - rRoi2;
				mySetRoi(imp0, xRoi2, yRoi2, rRoi2 * 2, null, Color.red);
				if (logCOMMENTI)
					IJ.log(">>>> CINQUE calcolo coordinate ROI ROSSA xRoi2= " + xRoi2 + " " + yRoi2 + " " + rRoi2 * 2);
				//
				// li devo memorizzare in ImageJ, in modo che le immagini successive abbiano
				// questa posizione, senza piu'chiederla (che palle se continua a chiedertela)
				//
				ImageStatistics stat1 = null;
				double[] out2 = null;
				String verifica = "";

				// effettua i calcoli per ogni IMMAGINE
				impStack.setSliceWithoutUpdate(1);

				// lo stackBuilder ha messo l'header di ogni immagine, posso ora andare ad
				// interrogarle

				ArrayList<Double> arrArea = new ArrayList<Double>();
				ArrayList<Double> arrMeanRow = new ArrayList<Double>();
				ArrayList<Double> arrMeanNorm = new ArrayList<Double>();
				ArrayList<Double> arrBvalue = new ArrayList<Double>();
				ArrayList<String> arrGradient = new ArrayList<String>();
				ArrayList<Double> arrLogMeanNorm = new ArrayList<Double>();
				ArrayList<Double> arrADCb = new ArrayList<Double>();

				int len1 = 0;

				// in base ai dati posizionamento Roi1 vado a calcolare i dati posizionamento
				// Roi2 (che ha diametro diverso)

				for (int i1 = 0; i1 < frames; i1++) {
					int slice = i1 + 1;
					IJ.runMacro("close(\"\\\\Others\");");

					ImagePlus imp1 = myImageFromStack(impStack, slice);
					// recupero i TAG che avevo messo come titolo della slice e li accodo a verifica
					String title = imp1.getTitle();
					verifica += "# slice " + slice + "" + title + " ";
					if (logCOMMENTI)
						IJ.log("SEI analizzo slice " + slice + " dati " + title + " ROI ROSSA xRoi2= " + xRoi2 + " "
								+ yRoi2 + " " + rRoi2 * 2);
					mySetRoi(imp1, xRoi2, yRoi2, rRoi2 * 2, null, Color.red);

					stat1 = imp1.getStatistics();
					double area = stat1.area * dimPixel * dimPixel; // l'area che ottengo e'in pixel, per ottenere mmq
																	// devo moltiplicare per dimPixel al quadrato
					// MyLog.waitHere("area[mmq]= " + area * dimPixel * dimPixel);
					double meanRow = stat1.mean;

					if (i1 == 0) // la prima immagine ha B=0 e fornisce la base
						meanBase = stat1.mean;

					double Bvalue = ReadDicom.readDouble(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_B_VALUE));
					double meanNorm1 = meanRow / meanBase;
					int precision = 9;
					///
					// poiche' in ResultsTable i valori sono arrotondati massimo a 9 digits succede
					/// che il log, invece viene calcolato sui 18 e rotti digits. Questo comporta
					/// che poi trovo differenze tra un calcolo di controllo fatto in excel o con la
					/// calcolatrice, rispetto al log calcolato da ImageJ. Se invece tronco prima di
					/// calcolare il log non ho questi problemi.
					///
					double meanNorm = truncate(meanNorm1, precision);
					double logMeanNorm = -Math.log(meanNorm);

					// appoggio i dati riordinati in alcuni array

					arrArea.add(area);
					arrMeanRow.add(meanRow);
					arrMeanNorm.add(meanNorm);
					arrLogMeanNorm.add(logMeanNorm);
					arrADCb.add(logMeanNorm / Bvalue);
					arrBvalue.add(Bvalue);
				}

				len1 = arrMeanNorm.size();
				if (len1 > 2) {
					out2 = regressor(arrMeanNorm, arrBvalue, arrGradient);
				}

				//
				// ho terminato l'eleborazione, ora posso stampare i dati nella ResultsTable
				//

				for (int i1 = 0; i1 < arrArea.size(); i1++) {

					rt.incrementCounter();
					rt.addValue(t1, "Bvalue");
					rt.addValue(s2, arrBvalue.get(i1));
					rt.addValue(s3, stat1.roiX);
					rt.addValue(s4, stat1.roiY);
					rt.addValue(s5, stat1.roiWidth);
					rt.addValue(s6, stat1.roiHeight);

					rt.incrementCounter();
					rt.addValue(t1, "Area");
					rt.addValue(s2, arrArea.get(i1));
					rt.addValue(s3, stat1.roiX);
					rt.addValue(s4, stat1.roiY);
					rt.addValue(s5, stat1.roiWidth);
					rt.addValue(s6, stat1.roiHeight);

					rt.incrementCounter();
					rt.addValue(t1, "Mean");
					rt.addValue(s2, arrMeanRow.get(i1));
					rt.addValue(s3, stat1.roiX);
					rt.addValue(s4, stat1.roiY);
					rt.addValue(s5, stat1.roiWidth);
					rt.addValue(s6, stat1.roiHeight);

					rt.incrementCounter();
					rt.addValue(t1, "MeanNorm");
					rt.addValue(s2, arrMeanNorm.get(i1));
					rt.addValue(s3, stat1.roiX);
					rt.addValue(s4, stat1.roiY);
					rt.addValue(s5, stat1.roiWidth);
					rt.addValue(s6, stat1.roiHeight);

					rt.incrementCounter();
					rt.addValue(t1, "logMeanNorm");
					rt.addValue(s2, arrLogMeanNorm.get(i1));
					rt.addValue(s3, stat1.roiX);
					rt.addValue(s4, stat1.roiY);
					rt.addValue(s5, stat1.roiWidth);
					rt.addValue(s6, stat1.roiHeight);
				}

				if (len1 > 2) {

					rt.incrementCounter();
					rt.addValue(t1, "coeffAngolare ");
					rt.addValue(s2, out2[0]);
					rt.addValue(s3, stat1.roiX);
					rt.addValue(s4, stat1.roiY);
					rt.addValue(s5, stat1.roiWidth);
					rt.addValue(s6, stat1.roiHeight);

					rt.incrementCounter();
					rt.addValue(t1, "intercetta");
					rt.addValue(s2, out2[1]);
					rt.addValue(s3, stat1.roiX);
					rt.addValue(s4, stat1.roiY);
					rt.addValue(s5, stat1.roiWidth);
					rt.addValue(s6, stat1.roiHeight);

					rt.incrementCounter();
					rt.addValue(t1, "verifica");
					rt.addValue(s2, verifica);
					rt.addValue(s3, 0);
					rt.addValue(s4, 0);
					rt.addValue(s5, 0);
					rt.addValue(s6, 0);
				}

				/// la seguente macro scritta in questo modo chiude tutte le finestre, eccetto
				/// l'attuale E FUNZIONA'
				IJ.runMacro("close(\"\\\\Others\");");

				if (verbose && !test && !valid2) {
					rt.show("Results");
				}

				if ((autoCalled && !test && !valid2)) {
					accetta = MyMsg.accettaMenu();
				} else {
					if (!test && !valid2) {
						accetta = MyMsg.msgStandalone();
					} else {
						accetta = test || valid2;
					}
				}
			} while (!accetta);
		}

		return rt;
	}

	public static void mySetRoi(ImagePlus imp1, int xroi, int yroi, int droi, Overlay over, Color color) {

		imp1.setRoi(new OvalRoi(xroi, yroi, droi, droi));
		if (imp1.isVisible()) {
			imp1.getWindow().toFront();
		}
		imp1.getRoi().setStrokeWidth(1.1);

		if (color != null) {
			imp1.getRoi().setStrokeColor(color);
		}
		if (over != null) {
			over.addElement(imp1.getRoi());
		}

	}

	public static void mySetRoi(ImagePlus imp1, double xroi, double yroi, double droi, Overlay over, Color color) {

		imp1.setRoi(new OvalRoi(xroi, yroi, droi, droi));
		if (imp1.isVisible()) {
			imp1.getWindow().toFront();
		}
		// imp1.getRoi().setStrokeWidth(1.1);
		imp1.getRoi().setStrokeWidth(0.5);

		if (color != null) {
			imp1.getRoi().setStrokeColor(color);
		}
		if (over != null) {
			over.addElement(imp1.getRoi());
		}

	}

	public String[] loadPath(String autoArgs) {
		String fileDir = Prefs.get("prefer.string1", "./test2/");
		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		String[] path = new String[vetRiga.length];
		for (int i1 = 0; i1 < vetRiga.length; i1++) {
			path[i1] = TableSequence.getPath(iw2ayvTable, vetRiga[i1]);
		}
		return path;
	}

	/**
	 * Costruisce uno stack a partire dal path delle immagini e lo riordina secondo
	 * la posizione delle fette.
	 *
	 * @param path vettore contenente il path delle immagini
	 * @return ImagePlus contenente lo stack generato
	 */
	public static ImagePlus stackBuilder2(String[] path, boolean verbose) {

		if ((path == null) || (path.length == 0)) {
			if (verbose) {
				IJ.log("stackBuilder path==null or 0 length.");
			}
			return null;
		}
		ImagePlus imp0 = UtilAyv.openImageNoDisplay(path[0], true);
		int rows = imp0.getHeight();
		int columns = imp0.getWidth();
		ImageStack newStack = new ImageStack(rows, columns);
		String[] sequence = new String[path.length];

		// originariamente lo StackBuilder ordinava le immagini secondo la posizione,
		// qui le ordina secondo il TAG DICOM_SEQUENCE_NAME = "0018,0024"
		for (int w1 = 0; w1 < path.length; w1++) {
			imp0 = UtilAyv.openImageNoDisplay(path[w1], true);
			// String dicomPosition = ReadDicom.readDicomParameter(imp0,
			// MyConst.DICOM_IMAGE_POSITION);
			String dicomSequenceName = ReadDicom.readDicomParameter(imp0, MyConst.DICOM_SEQUENCE_NAME);
			sequence[w1] = ReadDicom.readSubstring(dicomSequenceName, 3);
		}

		String[] pathSortato = bubbleSortPathSequence(path, sequence);

		for (int w1 = 0; w1 < pathSortato.length; w1++) {
			// ImagePlus imp1 = UtilAyv.openImageNormal(path[w1]);
			ImagePlus imp1 = UtilAyv.openImageNoDisplay(path[w1], true);

			String AAA = piedeDiPorcoNormal(path[w1], "0019,100C");
			String BBB = piedeDiPorcoDouble(path[w1], "0019,100E");
			// IJ.log("AAA= " + AAA + " BBB= " + BBB);

			ImageProcessor ip1 = imp1.getProcessor();
			if (w1 == 0) {
				newStack.update(ip1);
			}
			///// ============ 21/05/2025 ============================
			///// maschero nel titolo i parametri 0019,100C e 0019,100E, in questo modo
			///// il tutto viene sortato assieme all'immagine'
			//// =====================================================
			String sliceInfo1 = "# gradiente " + AAA + " direzioni " + BBB + " #";
			String sliceInfo2 = (String) imp1.getProperty("Info");
			// aggiungo i dati header alle singole immagini dello stack
			if (sliceInfo2 != null) {
				sliceInfo1 += "\n" + sliceInfo2;
			}

			newStack.addSlice(sliceInfo1, ip1);
		}
		// 180419 aggiunto eventuale codice del nome immagine anche allo stack
		File f = new File(path[0]);
		String nome1 = f.getName();
		String nome2 = nome1.substring(0, 5);
		ImagePlus newImpStack = new ImagePlus(nome2 + "_newSTACK", newStack);
		if (pathSortato.length == 1) {
			String sliceInfo3 = imp0.getTitle();
			sliceInfo3 += "\n" + (String) imp0.getProperty("Info");
			newImpStack.setProperty("Info", sliceInfo3);
		}
		newImpStack.getProcessor().resetMinAndMax();
		return newImpStack;
	}

	/**
	 * forse qui il sort non viene sfruttato, vedo che sono gia'in ordine i dati
	 * ricevutio da sequenze
	 * 
	 * @param path
	 * @param sequence
	 * @return
	 */
	public static String[] bubbleSortPathSequence(String[] path, String[] sequence) {

		if ((path == null) || (sequence == null) || !(path.length == sequence.length)) {
			return null;
		}
		if (path.length < 2) {
			return path;
		}
		//
		// bubblesort
		//
		String[] sortedPath = new String[path.length];
		sortedPath = path;

		for (int i1 = 0; i1 < path.length; i1++) {
			for (int i2 = 0; i2 < path.length - 1 - i1; i2++) {
				double seq1 = ReadDicom.readDouble(sequence[i2]);
				double seq2 = ReadDicom.readDouble(sequence[i2 + 1]);
				if (seq1 < seq2) {
					String positionSwap = sequence[i2];
					sequence[i2] = sequence[i2 + 1];
					sequence[i2 + 1] = positionSwap;
					String pathSwap = sortedPath[i2];
					sortedPath[i2] = sortedPath[i2 + 1];
					sortedPath[i2 + 1] = pathSwap;
				}
			}
		}
		// bubblesort end
		return sortedPath;
	}

	/**
	 *
	 * @param path
	 * @param slicePosition
	 * @return
	 */

	public static double[] regressor(ArrayList<Double> arrMeanNorm, ArrayList<Double> arrBvalue,
			ArrayList<String> arrGradient) {

		//
		// calcolo il Log dei valori normalizzati
		//
		double[] vetMeanNorm = ArrayUtils.arrayListToArrayDouble(arrMeanNorm);
		double[] bval = ArrayUtils.arrayListToArrayDouble(arrBvalue);

		double[] vetLogMeanNorm = new double[vetMeanNorm.length];
		for (int i1 = 0; i1 < vetMeanNorm.length; i1++) {
			vetLogMeanNorm[i1] = -Math.log(vetMeanNorm[i1]);
		}

		//
		// dopo avere fatto il logaritmo dei valori, applico la regressione lineare
		// utilizzando quella fornita da ImageJ nel CurveFitter
		//
		//
		CurveFitter curveFitter1 = new CurveFitter(bval, vetLogMeanNorm);
		curveFitter1.doFit(CurveFitter.STRAIGHT_LINE);
		// String resultString1 = curveFitter1.getResultString();
		double[] params1 = curveFitter1.getParams();

		//
		// preparo i risultati per l'esportazione, visto che ho un esempio fatto in
		// excel, da buon ignorante, esporto i paramentri del fit con lo stesso ordine
		// (invertiti rispetto ad ImageJ
		//

		double[] out1 = new double[2];
		out1[0] = params1[1];
		out1[1] = params1[0];

		return out1;
	}

	public static double truncate(double number, int precision) {
		double prec = Math.pow(10, precision);
		int integerPart = (int) number;
		double fractionalPart = number - integerPart;
		fractionalPart *= prec;
		int fractPart = (int) fractionalPart;
		fractionalPart = (double) (integerPart) + (double) (fractPart) / prec;
		return fractionalPart;
	}

	/***
	 * Questo workaround deriva da ReadAscconv e va utilizzato per leggere parametri
	 * dell'header ignorati da ImageJ, solitamente legati all'incauto passaggio
	 * delle immagini attraverso i PACS. (CAPRE, CAPRE, CAPRE ....)
	 * 
	 * @param path1
	 * @param ricerca
	 * @return
	 */

	public static String piedeDiPorcoNormal(String fileName1, String tag1) {

		int len1;
		String out1 = "";
		String reversed1 = new StringBuilder(8).append(tag1, 2, 4).append(tag1, 0, 2).append(tag1, 7, 9)
				.append(tag1, 5, 7).toString();
		byte[] x1 = hexStringToByteArray(reversed1);
		StringBuilder sb1 = new StringBuilder();
		for (byte b : x1) {
			sb1.append(String.format("%02X ", b));
		}
		// MyLog.waitHere("ricerco tag1= " + tag1 + " hex= " + sb1);

		String tag2 = "7FE0,0010"; // Pixel Data Start (Fine Header)
		String reversed2 = new StringBuilder(8).append(tag2, 2, 4).append(tag2, 0, 2).append(tag2, 7, 9)
				.append(tag2, 5, 7).toString();
		byte[] x2 = hexStringToByteArray(reversed2);

		try {
			BufferedInputStream f1 = new BufferedInputStream(new FileInputStream(fileName1));
			len1 = f1.available();
			byte[] buffer1 = new byte[len1];
			f1.read(buffer1, 0, len1); // get copy of entire file as byte[]
			f1.close();

			int offset1 = localizeHexWord(buffer1, x2, buffer1.length);
			// IJ.log("hex fine header= " + sb1 + " offset1= " + offset1);
			int offset2 = localizeHexWord(buffer1, x1, offset1);
			short len2 = Short.parseShort(byte2hex(buffer1[offset2 + 4]), 16);
			offset2 = offset2 + 8;
			byte[] buffer2 = new byte[len2];
			for (int i1 = 0; i1 < len2; i1++) {
				buffer2[i1] = buffer1[offset2 + i1];
			}

			// double
			// val2=ByteBuffer.wrap(buffer2).order(ByteOrder.LITTLE_ENDIAN).getDouble());

			out1 = new String(buffer2);

			// MyLog.waitHere("output >>> " + out1);

		} catch (Exception e) {
			IJ.showMessage("piedeDiPorco>>> ", "Exception " + "\n \n\"" + e.getMessage() + "\"");
		}
		return out1;
	}

	/***
	 * Legge parametri dall'header DICOM senza utilizzare ImageJ. In questa versione
	 * i dati vengono letti in forma di una terna DoublePrecision 64 bit,
	 * all'interno di una stringa. NOTA BENE: il tag va fornito come definito da
	 * NEMA, ad esempio "0019,100E", poi, internamente ed automagicamente vengono
	 * effettuate le opportune inversioni dei byte, poichè il TAG visto in un editor
	 * HEX diventa: 19000E10
	 * 
	 * ATTENZIONE: TESTATO E VERIFICATO I VALORI - DI SOLITO HO TERNE DI -1/0/0
	 * OPPURE 0/-1/0 OPPURE 0/0/1 MA HO ANCHE TROVATO -1/-3.9999996e-004/0 QUINDI SO
	 * CHE PUO' SUCCEDERE DI TROVARE VALORI ALLA CAXXXO
	 * 
	 * @param path1
	 * @param tag1
	 * @return
	 */

	public static String piedeDiPorcoDouble(String path1, String tag1) {
		int len1;

		String reversed1 = new StringBuilder(8).append(tag1, 2, 4).append(tag1, 0, 2).append(tag1, 7, 9)
				.append(tag1, 5, 7).toString();
		byte[] x1 = hexStringToByteArray(reversed1);
		StringBuilder sb1 = new StringBuilder();
		for (byte b : x1) {
			sb1.append(String.format("%02X ", b));
		}
		// MyLog.waitHere("ricerco tag1= " + tag1 + " hex= " + sb1);

		String tag2 = "7FE0,0010"; // Pixel Data Start (Fine Header)
		String reversed2 = new StringBuilder(8).append(tag2, 2, 4).append(tag2, 0, 2).append(tag2, 7, 9)
				.append(tag2, 5, 7).toString();
		byte[] x2 = hexStringToByteArray(reversed2);

		String out1 = "";
		byte[] bufferA = new byte[8];
		byte[] bufferB = new byte[8];
		byte[] bufferC = new byte[8];

//		ByteBuffer.wrap(bufferB).putDouble(641.5);

		try {
			BufferedInputStream f1 = new BufferedInputStream(new FileInputStream(path1));
			len1 = f1.available();
			byte[] buffer1 = new byte[len1];
			f1.read(buffer1, 0, len1); // get copy of entire file as byte[]
			f1.close();

			int offset1 = localizeHexWord(buffer1, x2, buffer1.length);
			// IJ.log("hex fine header= " + sb1 + " offset1= " + offset1);
			int offset2 = localizeHexWord(buffer1, x1, offset1);
			// IJ.log("tag offset2= " + offset2);
			short len2 = Short.parseShort(byte2hex(buffer1[offset2 + 4]), 16);
			// IJ.log("len2= " + len2);
			offset2 = offset2 + 8;

			if (len2 != 24) {
				return "MISS";
			} else {
				int i2 = 0;
				for (int i1 = 0; i1 < 8; i1++) {
					bufferA[i2++] = buffer1[offset2 + i1];
				}
				i2 = 0;
				for (int i1 = 8; i1 < 16; i1++) {
					bufferB[i2++] = buffer1[offset2 + i1];
				}
				i2 = 0;
				for (int i1 = 16; i1 < 24; i1++) {
					bufferC[i2++] = buffer1[offset2 + i1];
				}
			}

			double valA = ByteBuffer.wrap(bufferA).order(ByteOrder.LITTLE_ENDIAN).getDouble();
			double valB = ByteBuffer.wrap(bufferB).order(ByteOrder.LITTLE_ENDIAN).getDouble();
			double valC = ByteBuffer.wrap(bufferC).order(ByteOrder.LITTLE_ENDIAN).getDouble();

//			MyLog.waitHere("valA= " + valA + " valB= " + valB + " valC= " + valC);

			// assumo di avere una terna di valori double precision 64 bit, quindi preparo
			// tre buffers (per ora non faccio loops)

			out1 = valA + "/" + valB + "/" + valC;
			// IJ.log("output >>> " + out1);
		} catch (Exception e) {
			IJ.showMessage("piedeDiPorcoDouble>>> ", "Exception DOUBLEPORKFEET" + "\n \n\"" + e.getMessage() + "\"");
		}
		return out1;
	}

	/***
	 * conversione da string hexto byte array s1 deve essere di lunghezza pari
	 * 
	 * @param s1
	 * @return
	 */
	public static byte[] hexStringToByteArray(String s2) {
		String s1 = s2.replace(",", "");
		int len = s1.length();
		byte[] data = new byte[len / 2];
		for (int i1 = 0; i1 < len; i1 += 2) {
			data[i1 / 2] = (byte) ((Character.digit(s1.charAt(i1), 16) << 4) + Character.digit(s1.charAt(i1 + 1), 16));
		}
		return data;

	}

	public static int localizeHexWord(byte[] bImage, byte[] what, int limit) {
		int conta = 0;
		int locazione = 0;

//		 IJ.log("what =" + byte2hex(what[0]) + byte2hex(what[1])
//		 + byte2hex(what[2]) + byte2hex(what[3]));

		for (int i1 = 0; i1 < limit - 4; i1++) {

			if (bImage[i1 + 0] == what[0] && bImage[i1 + 1] == what[1] && bImage[i1 + 2] == what[2]
					&& bImage[i1 + 3] == what[3]) {
				locazione = i1;
				conta++;
//				 IJ.log("conta=" + conta + " locazione=" + locazione);
				break;
			}
		}

		if (conta > 0) {
			return locazione;
		} else {
			return -1; // non trovato
		}
	}

	public static String byte2hex(byte by) {
		char[] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		char[] buf2 = new char[2];
		buf2[1] = hexDigits[by & 0xf];
		by >>>= 4;
		buf2[0] = hexDigits[by & 0xf];
		return new String(buf2);
	} // end byte2hex

	/**
	 * estrae una singola slice da uno stack. Estrae anche i dati header
	 * 
	 * @param stack stack contenente le slices
	 * @param slice numero della slice da estrarre, deve partire da 1, non e'
	 *              ammesso lo 0
	 * @return ImagePlus della slice estratta
	 */
	public static ImagePlus myImageFromStack(ImagePlus stack, int slice) {

		if (stack == null) {
			IJ.log("imageFromStack.stack== null");
			return null;
		}
		// IJ.log("stack bitDepth= "+stack.getBitDepth());
		ImageStack imaStack = stack.getImageStack();
		if (imaStack == null) {
			IJ.log("imageFromStack.imaStack== null");
			return null;
		}
		if (slice == 0) {
			IJ.log("imageFromStack.requested slice 0!");
			return null;

		}
		if (slice > stack.getStackSize()) {
			IJ.log("imageFromStack.requested slice > slices!");
			return null;
		}

		ImageProcessor ipStack = imaStack.getProcessor(slice);

		// String titolo = "** " + slice + " **";
		String titolo = imaStack.getShortSliceLabel(slice);
		String sliceInfo1 = imaStack.getSliceLabel(slice);

		ImagePlus imp = new ImagePlus(titolo, ipStack);
		imp.setProperty("Info", sliceInfo1);
		return imp;
	}

}
