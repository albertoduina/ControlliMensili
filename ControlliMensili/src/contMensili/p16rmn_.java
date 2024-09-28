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
import ij.io.FileSaver;
import ij.measure.CurveFitter;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
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

		UtilAyv.setMyPrecision();

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
		boolean valid = Prefs.getBoolean(".prefer.p16rmn_valid", false);
		int userSelection1 = 0;

		do {
			if (valid) {
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
		UtilAyv.afterWork();
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
		ResultsTable rt = null;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		Overlay over1 = new Overlay();
		Overlay over2 = new Overlay();
		Overlay over3 = new Overlay();
		String path1 = path[0];
		int count = 0;

		//
		// carico il precedente posizionamento, memorizzato nelle preferenze
		//
		double prefx1 = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_xroi", "" + (96 / 2 - 5)));
		double prefy1 = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_yroi", "" + (96 / 2 - 5)));
		double prefr1 = ReadDicom.readDouble(Prefs.get("prefer.p16rmn_droi", "" + 10));
		// che stranezza per la lettura del boolean serve il punto BOH????
		String prefdir1 = Prefs.getString(".prefer.p16rmn_predir", "Z");
		boolean prefvalid1 = Prefs.getBoolean(".prefer.p16rmn_valid", false);
		// MyLog.waitHere("preferences= " + prefx1 + " " + prefy1 + " " + prefr1 + "
		// valid= " + valid);
		boolean valid2 = false;

		do {

			ImagePlus impStack = stackBuilder(path, true);
			int frames = path.length;

			impStack.setSliceWithoutUpdate(1);

			String name1 = ReadDicom.readDicomParameter(impStack, MyConst.DICOM_SERIES_DESCRIPTION);
			String dir = name1.substring(4, 5);
			//
			// resetto il valid se mi cambia la direzione. Inoltre valid viene resettato
			// dando new a sequenze
			//
			if (dir.equals(prefdir1) && prefvalid1 && !dir.equals("_"))
				valid2 = true;
			else
				valid2 = false;

			// MyLog.waitHere("dir= " + dir + " predir1= " + predir1 + " valid1= " + valid1
			// + " valid2= " + valid2);

			if (valid2) {
				UtilAyv.openImageNoDisplay(path1, true);
			} else {
				UtilAyv.showImageMaximized(impStack);
			}

			// ========================================================
			// QUI INIZIO AD ESEGUIRE LE STESSE COSE DELLA MACRO
			// ========================================================

			// copio i dati iniziali ROI fantoccio da quelli memorizzati dell'ultima
			// misura
			// comunque Lorella conferma:
			// "Stessa ROI per tutte le immagini, se sono nella stessa direzione (ass, cor,
			// sag)"

			int diamRoi1 = (int) prefr1;
			int xRoi1 = (int) prefx1;
			int yRoi1 = (int) prefy1;
			mySetRoi(impStack, xRoi1, yRoi1, diamRoi1, null, Color.green);

			if (!valid2) {

				int resp = 0;

				if (!test) {
					resp = ButtonMessages.ModelessMsg(
							"Posizionare ROI diamFantoccio e premere CONTINUA,  altrimenti, se l'immagine NON E'ACCETTABILE premere ANNULLA per passare alle successive",
							"CONTINUA", "ANNULLA");
				}

				if (resp == 1) {
					return null;
				}

				ImagePlus impActive1 = WindowManager.getCurrentImage();
				Rectangle boundingRectangle2 = impActive1.getRoi().getBounds();
				int diamRoi2 = boundingRectangle2.width;
				int xRoi2 = boundingRectangle2.x;
				int yRoi2 = boundingRectangle2.y;

				Prefs.set("prefer.p16rmn_xroi", Integer.toString(xRoi2));
				Prefs.set("prefer.p16rmn_yroi", Integer.toString(yRoi2));
				Prefs.set("prefer.p16rmn_droi", Integer.toString(diamRoi2));
				// ELIMINATO VALID XCHE IMMAGINI IN 3 DIREZIONI

				Prefs.set("prefer.p16rmn_predir", dir);
				Prefs.set("prefer.p16rmn_valid", true);

			}

			// leggo dove hanno posizionato la ROI fantoccio sull'immagine attiva, questi
			// sono i dati definitivi

			//
			// li devo memorizzare in ImageJ, in modo che le immagini successive abbiano
			// questa posizione, senza piu'chiederla (che palle se continua a chiedertela)
			//

			// MyLog.waitHere("" + count++ + " analizzo= " + impStack.getTitle());

			TableCode tc11 = new TableCode();
			String[][] tabCodici = tc11.loadMultipleTable("codici", ".csv");

			ImagePlus imp0 = MyStackUtils.imageFromStack(impStack, 1);

			String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp0, tabCodici, VERSION, autoCalled);
			rt = ReportStandardInfo.putSimpleStandardInfoRT_new(info1);
			rt.showRowNumbers(true);
			String t1 = "TESTO";
			String s2 = "VALORE";
			String s3 = "roi_x";
			String s4 = "roi_y";
			String s5 = "roi_b";
			String s6 = "roi_h";

			ImageStatistics stat1 = imp0.getStatistics();
			double area1 = stat1.area;
			double mean1 = stat1.mean;
			double[] out2 = null;

			// effettua i calcoli per ogni IMMAGINE
			impStack.setSliceWithoutUpdate(1);
			double meanBase = stat1.mean;

			// lo stackBuilder ha messo l'header di ogni immagine, posso ora andare ad
			// interrogarle

			ArrayList<Double> arrArea = new ArrayList<Double>();
			ArrayList<Double> arrMeanRow = new ArrayList<Double>();
			ArrayList<Double> arrMeanNorm = new ArrayList<Double>();
			ArrayList<Double> arrBvalue = new ArrayList<Double>();
			ArrayList<String> arrGradient = new ArrayList<String>();

			int len1 = 0;

			for (int i1 = 0; i1 < frames; i1++) {
				int slice = i1 + 1;
				ImagePlus imp1 = MyStackUtils.imageFromStack(impStack, slice);
				ImageStatistics stat2 = imp1.getStatistics();
				double area = stat2.area;
				double meanRow = stat2.mean;
				double meanNorm = meanRow / meanBase;

				double Bvalue = ReadDicom.readDouble(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_B_VALUE));
				String sName = ReadDicom.readDicomParameter(imp1, MyConst.DICOM_SEQUENCE_NAME);
				int len = sName.length();

				// appoggio i dati riordinati in alcuni array

				arrArea.add(area);
				arrMeanRow.add(meanRow);
				arrMeanNorm.add(meanNorm);
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
				//
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
				rt.addValue(t1, "Bvalue");
				rt.addValue(s2, arrBvalue.get(i1));
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
	public static ImagePlus stackBuilder(String[] path, boolean verbose) {

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
		String[] slicePos2 = new String[path.length];

		for (int w1 = 0; w1 < path.length; w1++) {
			imp0 = UtilAyv.openImageNoDisplay(path[w1], true);
			String dicomPosition = ReadDicom.readDicomParameter(imp0, MyConst.DICOM_IMAGE_POSITION);
			slicePos2[w1] = ReadDicom.readSubstring(dicomPosition, 3);
		}

		String[] pathSortato = bubbleSortPath(path, slicePos2);

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
	 *
	 * @param path
	 * @param slicePosition
	 * @return
	 */
	public static String[] bubbleSortPath(String[] path, String[] slicePosition) {

		if ((path == null) || (slicePosition == null) || !(path.length == slicePosition.length)) {
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
				double position1 = ReadDicom.readDouble(slicePosition[i2]);
				double position2 = ReadDicom.readDouble(slicePosition[i2 + 1]);
				if (position1 > position2) {
					String positionSwap = slicePosition[i2];
					slicePosition[i2] = slicePosition[i2 + 1];
					slicePosition[i2 + 1] = positionSwap;
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

//		ArrayList<Double> norMeanX = new ArrayList<Double>();
//		ArrayList<Double> norMeanY = new ArrayList<Double>();
//		ArrayList<Double> norMeanZ = new ArrayList<Double>();
//		ArrayList<Double> Bvalue = new ArrayList<Double>();
		int count = 0;

//		//
//		// suddivisione dei valori di input a seconda della direzione del gradiente
//		//
//		for (int i1 = 0; i1 < arrMeanNorm.size(); i1++) {
//
//			String gradient = arrGradient.get(i1);
//			double val1 = arrMeanNorm.get(i1);
//			double val2 = arrBvalue.get(i1);
//			int val3 = (int) val2;
//			int val4 = 0;
//
//			switch (gradient) {
//			case "x":
//				norMeanX.add(val1);
//				val4 = val3;
//				count++;
//				break;
//			case "y":
//				norMeanY.add(val1);
//				if (val4 == val3)
//					count++;
//				break;
//			case "z":
//				norMeanZ.add(val1);
//				if (val4 == val3)
//					count++;
//				// scrivo il Bvalue una volta su 3
//				Bvalue.add(val2);
//				count = 0;
//				// }
//				break;
//			default:
//			}
//
//		}

//		MyLog.logArrayList(Bvalue, "Bvalue");
//		MyLog.logArrayList(norMeanX, "norMeanX");
//		MyLog.logArrayList(norMeanY, "norMeanY");
//		MyLog.logArrayList(norMeanZ, "norMeanZ");

		//
		// calcolo il Log dei valori normalizzati
		//
		double[] vetMeanNorm = ArrayUtils.arrayListToArrayDouble(arrMeanNorm);
		double[] bval = ArrayUtils.arrayListToArrayDouble(arrBvalue);

		double[] vetLogMeanNorm = new double[vetMeanNorm.length];
		for (int i1 = 0; i1 < vetMeanNorm.length; i1++) {
			vetLogMeanNorm[i1] = -Math.log(vetMeanNorm[i1]);

		}

//		MyLog.logVector(bval, "bval");
//		MyLog.logVector(vetLogMeanNorm, "vetLogMeanNorm");
//		MyLog.waitHere();
		//
		// dopo avere fatto il logaritmo dei valori, applico la regressione lineare
		// utilizzando quella fornita da ImageJ nel CurveFitter
		//
		//
		CurveFitter curveFitter1 = new CurveFitter(bval, vetLogMeanNorm);
		curveFitter1.doFit(CurveFitter.STRAIGHT_LINE);
		String resultString1 = curveFitter1.getResultString();
//		IJ.log(resultString1);
		double[] params1 = curveFitter1.getParams();
//		MyLog.logVector(params1, "params1");
//		IJ.log("risultato in excel 0.001963601       0.110669528");

//		String titolo = "";
//		Plot plot10 = curveFitter1.getPlot();
//		plot10.setColor(Color.RED);
//		plot10.show();

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

}
