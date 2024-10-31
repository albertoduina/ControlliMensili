package contMensili;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.StringTokenizer;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.NewImage;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.CurveFitter;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import utils.AboutBox;
import utils.ArrayUtils;
import utils.ButtonMessages;
import utils.ImageUtils;
import utils.InputOutput;
import utils.MyConst;
import utils.MyFileLogger;
import utils.MyLog;
import utils.MyMsg;
import utils.MyStackUtils;
import utils.MyVersionUtils;
import utils.ReadDicom;
import utils.ReportStandardInfo;
import utils.TableCode;
import utils.TableExpand;
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
 * Analizza DIFFUSIONE
 *
 *
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 *
 */

public class p16rmn_ implements PlugIn, Measurements {

	private static final int ABORT = 1;

	public static String VERSION = "DIFFUSIONE";

	private static String TYPE = " >> MISURA DIFFUSIONE_____________";

	private static String fileDir = "";
	private static boolean debug = true;
	private static boolean mylogger = true;

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
		TableExpand tc2 = new TableExpand();
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
		boolean step = false;
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
				step = true;
			case 5:
				String path1 = UtilAyv.imageSelection("SELEZIONARE PRIMA IMMAGINE...");
				if (path1 == null) {
					return 0;
				}
				String path2 = UtilAyv.imageSelection("SELEZIONARE SECONDA IMMAGINE...");
				if (path2 == null) {
					return 0;
				}
				boolean autoCalled = false;
				boolean verbose = true;
				boolean test = false;
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

		int nTokens = new StringTokenizer(autoArgs, "#").countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);

		// i numeri di tokens che possiamo ricevere
		// 31

//		if ((nTokens != 31) && (nTokens != 19)) {
//
//			// NUMERI DI IMMAGINI CHE SI POSSONO RICEVERE: UNA, NESSUNA, CENTOMILA!
//
//			MyMsg.msgParamError();
//			return 0;
//		}

		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);

		String[] path = loadPath(autoArgs);

		boolean retry = false;
		boolean step = false;
		ResultsTable rt = null;
		boolean valid1 = Prefs.getBoolean(".prefer.p16rmn_valid", false);
		// MyLog.waitHere("valid= " + valid1);

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
	@SuppressWarnings("deprecation")
	public static ResultsTable mainDiffusion(String[] path, String autoArgs, boolean autoCalled, boolean step,
			boolean verbose, boolean test) {

		boolean accetta = false;
		boolean valid2 = false;
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		Overlay over1 = new Overlay();
		String path1 = path[0];
		int count = 0;
		boolean reference = false;

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

		double preferencesX = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiX", "30"));
		double preferencesY = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiY", "30"));
		double preferencesD = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiD", "30"));
		valid2 = Prefs.getBoolean(".prefer.p16rmn_valid", false);

//		MyLog.waitHere(
//				"READ preferences X,Y,R= " + preferencesX + " ; " + preferencesY + " ; " + preferencesD + ";" + valid2);

		double xRoi0 = 9999;
		double yRoi0 = 9999;
		double xRoi2 = 9999;
		double yRoi2 = 9999;
		double rRoi2 = 10; // raggio = 10, diametro = 20
		double dRoi2 = rRoi2 * 2; // 10 * dimPixel; // raggio = 10, diametro = 20

		//
		ArrayList<String> subPathX = new ArrayList<String>();
		ArrayList<String> subPathY = new ArrayList<String>();
		ArrayList<String> subPathZ = new ArrayList<String>();
		// ArrayList<String> subPathW = new ArrayList<String>();
		String[] pathBB = null;

		ImagePlus imp00 = UtilAyv.openImageNoDisplay(path[0], true);
		//
		// inizio subito a scrivere Results
		//
		TableCode tc11 = new TableCode();
		String[][] tabCodici = tc11.loadMultipleTable("codici", ".csv");

		// imp0 = MyStackUtils.imageFromStack(impStack, 1);

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
				MyLog.waitHere("BELIN BELANDI!");

			}

		}
		String[] pathX = ArrayUtils.arrayListToArrayString(subPathX);
		String[] pathY = ArrayUtils.arrayListToArrayString(subPathY);
		String[] pathZ = ArrayUtils.arrayListToArrayString(subPathZ);
		// String[] pathW = ArrayUtils.arrayListToArrayString(subPathW);

		// String munk = null;
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
				xRoi0 = preferencesX;
				yRoi0 = preferencesY;
				rRoi0 = preferencesD;
				reference = false;
				break;
			case 1:
				rt.incrementCounter();
				rt.addValue(t1, "<---- direzione  Y ---->");
				pathBB = pathY;
				xRoi0 = preferencesX;
				yRoi0 = preferencesY;
				rRoi0 = preferencesD;
				reference = false;
				break;
			case 2:
				rt.incrementCounter();
				rt.addValue(t1, "<---- direzione  Z ---->");
				pathBB = pathZ;
				xRoi0 = preferencesX;
				yRoi0 = preferencesY;
				rRoi0 = preferencesD;
				reference = false;
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

				// ========================================================
				// predispongo la ROI con diametro fantoccio, in base ad essa viene poi
				// posizionata la roi diametro 20 per i calcoli
				// ========================================================

				ImagePlus imp0 = MyStackUtils.imageFromStack(impStack, 1);
				UtilAyv.showImageMaximized(imp0);

				// questa e' la roi verde esterna
				mySetRoi(imp0, xRoi0, yRoi0, rRoi0, null, Color.green);

				if (!valid2) {

					// MyLog.waitHere("valid= " + valid2);

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
//					ImagePlus impActive1 = WindowManager.getCurrentImage();
					Rectangle boundingRectangle1 = imp0.getRoi().getBounds();
					double dRoi1 = boundingRectangle1.width;
					rRoi1 = dRoi1 / 2;
					xRoi1 = boundingRectangle1.x;
					yRoi1 = boundingRectangle1.y;

					// ho impostato la Roi0 e modificata con Roi1, disegno la Roi2 in rosso solo a
					// scopo diagnostico
					// MyLog.waitHere("xRoi1= " + xRoi1 + " yRoi1= " + yRoi1 + " rRoi1= " + rRoi1);
					xRoi2 = xRoi1 + rRoi1 - rRoi2;
					yRoi2 = yRoi1 + rRoi1 - rRoi2;
					mySetRoi(imp0, xRoi2, yRoi2, rRoi2 * 2, null, Color.red);

					// DEVO DECIDERE COME SCRIVERE LE PREFERENZE NEL FILE

//					IJ.wait(100);

//					double preferencesX = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiX", "50"));
//					double preferencesY = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiY", "50"));
//					double preferencesR = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiR", "70/2"));
//					MyLog.waitHere("WRITE preferences X,Y,R= " + xRoi1 + " ; " + yRoi1 + " ; " + rRoi1 * 2);

					Prefs.set("prefer.p16rmn_roiX", Double.toString(xRoi1));
					Prefs.set("prefer.p16rmn_roiY", Double.toString(yRoi1));
					Prefs.set("prefer.p16rmn_roiD", Double.toString(rRoi1 * 2));
					Prefs.set("prefer.p16rmn_valid", valid2);

//
//					Prefs.set("prefer.p16rmn_predir", dir);
//
				}

				// leggo sull'immagine attiva, questi
				// sono i dati definitivi
				// leggo i dati delle preferenze

				double prefX = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiX", "30"));
				double prefY = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiY", "30"));
				double prefD = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_roiD", "30"));

				//
				// li devo memorizzare in ImageJ, in modo che le immagini successive abbiano
				// questa posizione, senza piu'chiederla (che palle se continua a chiedertela)
				//

				// MyLog.waitHere("" + count++ + " analizzo= " + impStack.getTitle());

//				ImageStatistics stat1 = imp0.getStatistics();
//				double area1 = stat1.area;
//				double mean1 = stat1.mean;
//				double[] out2 = null;

				ImageStatistics stat1 = null;
				double area1 = 0;
				double mean1 = 0;
				double[] out2 = null;

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
				ArrayList<Double> arrADCbT0 = new ArrayList<Double>();
				ArrayList<Double> arrScartoPercADC = new ArrayList<Double>();

				int len1 = 0;

				// in base ai dati posizionamento Roi1 vado a calcolare i dati posizionamento
				// Roi2 (che ha diametro diverso)

//				xRoi2 = xRoi1 + rRoi1 - rRoi2;
//				yRoi2 = yRoi1 + rRoi1 - rRoi2;
				xRoi2 = prefX + prefD - rRoi2;
				yRoi2 = prefY + prefD - rRoi2;

				for (int i1 = 0; i1 < frames; i1++) {
					int slice = i1 + 1;


					ImagePlus imp1 = MyStackUtils.imageFromStack(impStack, slice);

					// imp1.setRoi(new OvalRoi(xRoi2, yRoi2, rRoi2 * 2, rRoi2 * 2));


					mySetRoi(imp1, xRoi2, yRoi2, rRoi2 * 2, null, Color.red);
					stat1 = imp1.getStatistics();
					double area = stat1.area * dimPixel * dimPixel; // l'area che ottengo e'in pixel, per ottenere mmq
																	// devo moltiplicare per dimPixel al quadrato
					// MyLog.waitHere("area[mmq]= " + area * dimPixel * dimPixel);
					double meanRow = stat1.mean;

					if (i1 == 0) // la prima immagine ha B=0 e fornisce la base
						meanBase = stat1.mean;

					double Bvalue = ReadDicom.readDouble(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_B_VALUE));
					// if (Bvalue==0) MyLog.waitHere("Bvalue==0");
					String sName = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SEQUENCE_NAME);
//					IJ.log(""+i1+" sName= "+sName);
					int len = sName.length();

					double meanNorm1 = meanRow / meanBase;
					int precision = 9;

					///
					// poiche' in ResultsTable i valori sono arrotondati massimo a 9 digits succede
					/// che il log, invece viene calcolato sui 18 e rotti digits. Questo comporta
					/// che poi trovo differenze tra un calcolo di controllo fatto in excel o con la
					/// calcolatrice, rispetto al log calcolato da ImageJ. Se invece tronco prima di
					/// calcolare il log non ho questi problemi.
					///

//					if (meanNorm1 == 0)
//						MyLog.waitHere("area= " + area + "\nmeanRow= " + meanRow + "\nBvalue= " + Bvalue
//								+ "\nmeanNorm1 " + meanNorm1);

					double meanNorm = truncate(meanNorm1, precision);
//					if (meanNorm == 0)
//						MyLog.waitHere("meanNorm1= " + meanNorm1 + "\nprecision= " + precision);

					double logMeanNorm = -Math.log(meanNorm);
//					if (logMeanNorm == 0)
//						MyLog.waitHere("area= " + area + "\nmeanRow= " + meanRow + "\nBvalue= " + Bvalue
//								+ "\nmeanNorm1= " + meanNorm1 + "\nprecision= " + precision + "\nlogMeanNorm= "
//								+ logMeanNorm + "\nmeanNorm= " + meanNorm);

//					if (Bvalue > 2500)
//						MyLog.waitHere("meanRow= "+meanRow+" meanNorm= " + meanNorm + " logMeanNorm= " + logMeanNorm);

					// appoggio i dati riordinati in alcuni array

//					if (area == 0)
//						MyLog.waitHere("area == 0");
//					if (meanRow == 0)
//						MyLog.waitHere("meanRow == 0");
//					if (meanNorm == 0)
//						MyLog.waitHere("meanNorm == 0");
//					if (logMeanNorm == 0)
//						MyLog.waitHere("logMeanNorm == 0");
//					if ((logMeanNorm / Bvalue) == 0)
//						MyLog.waitHere("(logMeanNorm / Bvalue) == 0");
//					if (Bvalue == 0)
//						MyLog.waitHere("ATTENZIONE trovato Bvalue == 0 (TAG 0019,100C");
//					IJ.log("slice= " + slice + " xRoi2= " + xRoi2 + " yRoi2= " + yRoi2 + " rRoi2= " + rRoi2 + " area= "
//							+ area + " meanRow= " + meanRow + " meanNorm= " + meanRow + " Bvalue= " + Bvalue);

					arrArea.add(area);
					arrMeanRow.add(meanRow);
					arrMeanNorm.add(meanNorm);
					arrLogMeanNorm.add(logMeanNorm);
					arrADCb.add(logMeanNorm / Bvalue);
					// arrADCb.add(logMeanNorm/Bvalue-(CoeffT*deltaT));

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
				}
				// }

				// MyConst.CODE_FILE, MyConst.TOKENS4);

				// put values in ResultsTable

				// MyLog.logVector(info1, "info1");
				// MyLog.waitHere();

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
			ImagePlus imp1 = UtilAyv.openImageNoDisplay(path[w1], true);
			ImageProcessor ip1 = imp1.getProcessor();
			if (w1 == 0) {
				newStack.update(ip1);
			}
			String sliceInfo1 = imp1.getTitle();
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
		// MyLog.waitHere("nome2= "+nome2);
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
		String resultString1 = curveFitter1.getResultString();
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

}
