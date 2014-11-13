package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.ImageCalculator;
import ij.plugin.PlugIn;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.StringTokenizer;

import utils.AboutBox;
import utils.ButtonMessages;
import utils.MyAutoThreshold;
import utils.MyLog;
import utils.MyMsg;
import utils.MyConst;
import utils.CustomCanvasGeneric;
import utils.InputOutput;
import utils.MyVersionUtils;
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
 * Analizza il WARP , le rods vengono selezionate in automatico
 * 
 * 
 * @author Alberto Duina - SPEDALI CIVILI DI BRESCIA - Servizio di Fisica
 *         Sanitaria
 * 
 */

public class p17rmn_ implements PlugIn, Measurements {

	static final int ABORT = 1;

	public static String VERSION = "WARP";

	private static String TYPE = " >> CONTROLLO WARP____________________";

	// ---------------------------"01234567890123456789012345678901234567890"
	private static boolean debug = true;

	/**
	 * directory dati, dove vengono memorizzati ayv.txt e Results1.xls
	 */
	private static String fileDir = "";

	/**
	 * tabella coi dati di ayv.txt (generati da Sequenze)
	 */
	// String[][] iw2ayvTable;

	/**
	 * immagine da analizzare
	 */
	ImagePlus imp1;

	public void run(String args) {

		UtilAyv.setMyPrecision();

		if (IJ.versionLessThan("1.43k"))
			return;
		//
		// nota bene: le seguenti istruzioni devono essere all'inizio, in questo
		// modo il messaggio viene emesso, altrimenti si ha una eccezione
		//
		try {
			Class.forName("utils.IW2AYV");
		} catch (ClassNotFoundException e) {
			IJ.error("ATTENZIONE, manca il file iw2ayv_xxx.jar");
			return;
		}

		String className = this.getClass().getName();

		VERSION = className + "_build_" + MyVersion.getVersion()
				+ "_iw2ayv_build_" + MyVersionUtils.getVersion();

		fileDir = Prefs.get("prefer.string1", "none");

		int nTokens = new StringTokenizer(args, "#").countTokens();
		if (nTokens == 0) {
			manualMenu(0, "");
		} else {
			autoMenu(args);
		}
	}

	public int manualMenu(int preset, String testDirectory) {
		boolean retry = false;
		boolean step = false;
		int riga1 = 0;
		do {

			int userSelection1 = UtilAyv.userSelectionManual(VERSION, TYPE);
			switch (userSelection1) {
			case ABORT:
				new AboutBox().close();
				retry = false;
				return 0;
			case 2:
				// new AboutBox().about("Controllo Warp", this.getClass());
				new AboutBox().about("Controllo Warp",
						MyVersion.CURRENT_VERSION);
				retry = true;
				break;
			case 3:
				selfTestAyv();
				retry = true;
				break;
			case 4:
				step = true;
			case 5:
				String path1 = UtilAyv
						.imageSelection("SELEZIONARE IMMAGINE...");
				if (path1 == null)
					return 5;
				boolean autoCalled = false;
				boolean verbose = true;
				boolean test = false;
				boolean demo = true;
				boolean silent = false;
				int timeout = 0;
				mainWarp(path1, autoCalled, step, silent, verbose, test, demo,
						timeout);
				UtilAyv.afterWork();
				retry = false;
				return 5;
			}
		} while (retry);
		UtilAyv.afterWork();
		new AboutBox().close();
		return 0;
	}

	public int autoMenu(String autoArgs) {
		MyLog.appendLog(fileDir + "MyLog.txt", "p17 riceve " + autoArgs);

		boolean fast = Prefs.get("prefer.fast", "false").equals("true") ? true
				: false;

		ResultsTable result1 = null;
		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir
				+ MyConst.SEQUENZE_FILE);

		StringTokenizer st = new StringTokenizer(autoArgs, "#");
		int nTokens = st.countTokens();
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		if (vetRiga[0] == -1) {
			IJ.log("selfTestSilent.p7rmn_");
			selfTestSilent();
			return 0;
		}
		if (nTokens > 1) {
			MyMsg.msgParamError();
			return 0;
		}
		boolean retry = false;
		boolean step = false;
		if (fast) {
			retry = false;
			boolean autoCalled = true;
			boolean demo = false;
			boolean test = false;
			boolean silent = false;
			boolean verbose = true;
			int timeout = 3000;

			String path1 = TableSequence.getPath(iw2ayvTable, vetRiga[0]);

			result1 = mainWarp(path1, autoCalled, step, silent, verbose, test,
					demo, timeout);

			if (!(result1 == null))
				UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, result1);

			UtilAyv.afterWork();

		} else {
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
					// new AboutBox().about("Controllo Warp", this.getClass());
					new AboutBox().about("Controllo Warp",
							MyVersion.CURRENT_VERSION);
					retry = true;
					break;
				case 3:
					step = true;
				case 4:
					boolean verbose = true;
					boolean test = false;
					boolean autoCalled = true;
					iw2ayvTable = new TableSequence().loadTable(fileDir
							+ MyConst.SEQUENZE_FILE);
					String path1 = TableSequence.getPath(iw2ayvTable,
							vetRiga[0]);
					// ResultsTable rt = mainWarp(path1], autoCalled, step,
					// verbose,
					// test);
					// UtilAyv.saveResults(vetRiga, fileDir, iw2ayvTable, rt);

					UtilAyv.afterWork();
					retry = false;
					break;
				}
			} while (retry);
		}
		UtilAyv.afterWork();
		new AboutBox().close();
		return 0;
	}

	@SuppressWarnings("deprecation")
	public static ResultsTable mainWarp(String path1, boolean autoCalled,
			boolean step, boolean silent, boolean verbose, boolean test,
			boolean demo, int timeout) {
		boolean accetta = false;
		UtilAyv.setMeasure(MEAN + STD_DEV);
		ResultsTable rt = null;
		double dimPixel2 = 0;
		MyLog.waitHere("mainWarp " + path1);

		// --------------------------------------------------------------------------------------/
		// Qui si torna se la misura è da rifare
		// --------------------------------------------------------------------------------------/

		ImagePlus imp1 = UtilAyv.openImageNoDisplay(path1, verbose);

		int diam = 10;
		int[][] tabPunti = p17rmn_.automaticRoiPreparation3(imp1, diam, silent,
				timeout, demo);
		if (tabPunti == null) {
			imp1.show();
			MyLog.waitHere("tabPunti==null");
			return null;
		}
		dimPixel2 = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));
		String slicePos = ReadDicom.readSubstring(ReadDicom.readDicomParameter(
				imp1, MyConst.DICOM_IMAGE_POSITION), 3);
		int nPunti = tabPunti.length;

		String[][] tabCodici = TableCode.loadMultipleTable(MyConst.CODE_GROUP);

		String[] info1 = ReportStandardInfo.getSimpleStandardInfo(path1, imp1,
				tabCodici, VERSION, autoCalled);
		rt = ReportStandardInfo.putSimpleStandardInfoRT(info1);
		String t1 = "TESTO";
		String s2 = "coord_x";
		String s3 = "coord_y";
		String s4 = "dummy1";
		String s5 = "dummy2";
		String s6 = "dummy3";
		String s7 = "dummy4";

		rt.addLabel(t1, "ShiftCentrat");
		rt.addValue(s2, UtilAyv.convertToDouble(slicePos));
		String aux1 = "";
		int aux2 = 0;
		for (int i2 = 0; i2 < MyConst.P7_NUM_RODS; i2 = i2 + 2) {
			aux2++;
			aux1 = "Rod" + aux2 + "a";
			rt.incrementCounter();
			rt.addLabel(t1, aux1);
			rt.addValue(s2, tabPunti[i2][0]);
			rt.addValue(s3, tabPunti[i2][1]);
			rt.addValue(s4, 0);
			rt.addValue(s5, 0);
			rt.addValue(s6, 0);
			rt.addValue(s7, 0);
			rt.incrementCounter();
			aux1 = "Rod" + aux2 + "b";
			rt.addLabel(t1, aux1);
			rt.addValue(s2, tabPunti[i2 + 1][0]);
			rt.addValue(s3, tabPunti[i2 + 1][1]);
			rt.addValue(s4, 0);
			rt.addValue(s5, 0);
			rt.addValue(s6, 0);
			rt.addValue(s7, 0);
		}
		aux2 = 0;
		for (int i2 = MyConst.P7_NUM_RODS; i2 < MyConst.P7_TOTAL_NUM_POINTS; i2++) {
			aux2++;
			aux1 = "Cubo" + aux2;
			rt.incrementCounter();
			rt.addLabel(t1, aux1);
			rt.addValue(s2, tabPunti[i2][0]);
			rt.addValue(s3, tabPunti[i2][1]);
		}
		rt.incrementCounter();
		rt.addLabel(t1, "Spacing");
		rt.addValue(s2, dimPixel2);
		if (verbose && !test)
			rt.show("Results");

		return rt;
	}

	/***
	 * Ricerca automatica delle posizioni gels con AutoTreshold ed
	 * AnalyzeParticles
	 * 
	 * @param imp1
	 * @param diam2
	 * @param timeout
	 * @param demo
	 */
	public static int[][] automaticRoiPreparation3(ImagePlus imp1, int diam2,
			boolean silent, int timeout, boolean demo) {

		ImagePlus imp4 = imp1.duplicate();
		Overlay over1 = new Overlay();
		imp1.setOverlay(over1);
		// Overlay over2 = new Overlay();
		// imp5.setOverlay(over2);
		boolean verbose = true;

		int numRods1 = 36;

		// if (demo) {
		// UtilAyv.showImageMaximized(imp4);
		// MyLog.waitHere(listaMessaggi(0), debug, timeout);
		// }

		double dimPixel = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));

		// -------------------------------------------------------------------
		// REMEMBER: PER USARE ANALYZE PARTICLES DOBBIAMO
		// ANALIZZARE OGGETTI NERI SU SFONDO BIANCO
		// dovrei ottenere per l'esterno 32 oggetti con area
		// nel range tra 5.0 e 12.0 e per l'interno 4 oggetti
		// altrimenti effettuerò il threshold manuale
		// UTILIZZO 5 diverse strategie (in realtà sono un mix tra i settaggi di
		// MyAutoThreshold e l'uso di invert
		// -------------------------------------------------------------------

		int[] trovati = new int[3];
		int[] minArea = new int[3];
		int[] maxArea = new int[3];

		// creo anche un vettore di ImagePlus (non uno stack)
		ImagePlus[] vetImp = new ImagePlus[3];

		// per prima cosa effettuo il threshold "SIEMENS"

		UtilAyv.showImageMaximized(imp1);
		vetImp[0] = strategiaSIEMENS(imp4);
		UtilAyv.showImageMaximized(vetImp[0]);
		vetImp[1] = strategiaHITACHI(imp4);
		UtilAyv.showImageMaximized(vetImp[1]);
		vetImp[2] = strategiaGEMS(imp4);
		UtilAyv.showImageMaximized(vetImp[2]);
		MyLog.waitHere();

		ImagePlus impX1 = null;
		ResultsTable[] vetResults = new ResultsTable[vetImp.length];
		ResultsTable rtAux = null;
		for (int i1 = 0; i1 < vetImp.length; i1++) {
			impX1 = vetImp[i1];
			if (impX1 == null)
				continue;
			rtAux = analisi(vetImp[i1], verbose, timeout);
			vetResults[i1] = rtAux;
			if (rtAux != null) {
				trovati[i1] = rtAux.getCounter();
				if (trovati[i1] > 1) {
					double[] vetArea11 = rtAux.getColumnAsDoubles(rtAux
							.getColumnIndex("Area"));
					double[] lim11 = Tools.getMinMax(vetArea11);
					minArea[i1] = (int) Math.floor(lim11[0]);
					maxArea[i1] = (int) Math.ceil(lim11[1]);

				} else {
					// se la ResultsTable è lunga 0 scrivo tutti 0
					minArea[i1] = 0;
					maxArea[i1] = 0;
				}
			} else {
				// se la ResultsTable è null scrivo tutti 0
				trovati[i1] = 0;
				minArea[i1] = 0;
				maxArea[i1] = 0;
			}
		}

		// MyLog.logVector(trovati, "trovati");
		// MyLog.logVector(minArea, "minArea");
		// MyLog.logVector(maxArea, "maxArea");
		// MyLog.waitHere();

		// Analizzo i risultati, il primo che raggiunge l'obbiettivo viene
		// accettato, senza guardare gli altri. Se non si trova niente di
		// accettabile si passa il tutto all'AMANUENSE
		//
		int rodTot = 36;
		ResultsTable rt1 = null;

		for (int i1 = 0; i1 < trovati.length; i1++) {
			if ((trovati[i1] > rodTot - 5 && trovati[i1] < rodTot + 5)) {
				rt1 = vetResults[i1];
				break;
			}
		}

		if (rt1 == null) {
			// threshold manuale
			MyLog.waitHere("funzionamento manuale");
		}

		// ====================== centrale ==================

		// rt1.show("Results");
		// MyLog.waitHere();

		int xcol = rt1.getColumnIndex("XM");
		int ycol = rt1.getColumnIndex("YM");

		double[] vetX = rt1.getColumnAsDoubles(xcol);
		// MyLog.logVector(vetX, "vetX");

		double[] vetY = rt1.getColumnAsDoubles(ycol);
		// MyLog.logVector(vetY, "vetY");
		// MyLog.waitHere();

		// UtilAyv.showImageMaximized(imp1);
		double[] vetx1 = new double[vetX.length];
		double[] vety1 = new double[vetX.length];
		for (int i1 = 0; i1 < vetX.length; i1++) {
			vetx1[i1] = vetX[i1] / dimPixel;
			vety1[i1] = vetY[i1] / dimPixel;
		}

		Roi[] vetRoi = new Roi[vetX.length];
		for (int i1 = 0; i1 < vetX.length; i1++) {
			imp1.setRoi(new OvalRoi(vetx1[i1] - diam2 / 2, vety1[i1] - diam2
					/ 2, diam2, diam2));
			vetRoi[i1] = imp1.getRoi();
			imp1.getRoi().setStrokeColor(Color.green);
			over1.addElement(imp1.getRoi());
		}
		imp1.deleteRoi();

		int[] vetx2 = new int[vetx1.length];
		int[] vety2 = new int[vetx1.length];
		for (int i1 = 0; i1 < vetx1.length; i1++) {
			vetx2[i1] = (int) Math.round(vetx1[i1]);
			vety2[i1] = (int) Math.round(vety1[i1]);
		}
		imp1.setRoi(new PointRoi(vetx2, vety2, vetx1.length));

		if (vetX.length != numRods1) {
			// if (cw2.running)
			// cw2.close();
			// if (imp3 != null)
			// imp3.flush();
			if (!imp1.isVisible())
				UtilAyv.showImageMaximized(imp1);
			imp1.getWindow().toFront();
			IJ.setTool("multipoint");
			MyLog.waitHere(listaMessaggi(7), debug, timeout);
			MyLog.waitHere("Cliccare sulle RODS non selezionate, per annullare le RODS sbagliate,\n"
					+ "cliccare sul puntino rosso mentre si tiene premuto ALT,\nalla fine premere OK");
		}

		if (demo || !silent) {
			// if (cw2.running)
			// cw2.close();
			// if (imp3 != null)
			// imp3.flush();
			// imp3.close();
			if (!imp1.isVisible())
				UtilAyv.showImageMaximized(imp1);
			imp1.getWindow().toFront();
			if (demo)
				MyLog.waitHere(listaMessaggi(4), debug, timeout);
			else
				IJ.wait(2000);
		}

		if (vetx1.length == 0) {
			MyLog.waitHere("lunghezza Results=0");
			return null;
		}

		Polygon poli1 = imp1.getRoi().getPolygon();
		int nPunti;
		if (poli1 == null) {
			nPunti = 0;
		} else {
			nPunti = poli1.npoints;
		}

		int[] xPoints = poli1.xpoints;
		int[] yPoints = poli1.ypoints;
		int[][] tabPunti = new int[nPunti][2];
		for (int i1 = 0; i1 < nPunti; i1++) {
			tabPunti[i1][0] = xPoints[i1];
			tabPunti[i1][1] = yPoints[i1];
		}
		return tabPunti;
	}

	public static ImagePlus strategiaSIEMENS(ImagePlus imp1) {
		if (imp1 == null) {
			MyLog.waitHere();
			return null;
		}
		ImagePlus imp2 = p17rmn_.strategia0(imp1);
		ImagePlus imp3 = p17rmn_.strategia1(imp1);
		ImagePlus imp4 = p17rmn_.combina(imp2, imp3);
		return imp4;
	}

	public static ImagePlus strategiaHITACHI(ImagePlus imp1) {
		if (imp1 == null) {
			MyLog.waitHere();
			return null;
		}
		ImagePlus imp2 = p17rmn_.strategia4(imp1);
		ImagePlus imp3 = p17rmn_.strategia1(imp1);
		ImagePlus imp4 = p17rmn_.combina(imp2, imp3);
		return imp4;
	}

	public static ImagePlus strategiaGEMS(ImagePlus imp1) {
		if (imp1 == null) {
			MyLog.waitHere();
			return null;
		}
		ImagePlus imp2 = p17rmn_.strategia0(imp1);
		ImagePlus imp3 = p17rmn_.strategia1(imp1);
		ImagePlus imp4 = p17rmn_.combina(imp2, imp3);
		return imp4;
	}

	/***
	 * La strategia 0 è dedicata al circolo esterno di rods nelle macchine
	 * Siemens
	 * 
	 * @param imp1
	 * @return
	 */
	public static ImagePlus strategia0(ImagePlus imp1) {

		boolean noBlack = true;
		boolean noWhite = true;
		boolean doWhite = false;
		boolean doSet = false;
		boolean doLog = false;

		ImagePlus imp12 = MyAutoThreshold.threshold(imp1, "Mean", noBlack,
				noWhite, doWhite, doSet, doLog);
		// ora analizzo l'immagine cercando il profilo tondo del fantoccio,
		// riempirò l'esterno di nero
		int minSizePixels = 10000;
		int maxSizePixels = 300000;
		Roi roi0 = analisi0(imp12, minSizePixels, maxSizePixels);

		if (roi0 == null) {
			UtilAyv.showImageMaximized(imp1);
			UtilAyv.showImageMaximized(imp12);
			MyLog.waitHere("roi0==null");
			return null;
		}
		ImagePlus imp2 = imp12.duplicate();
		ImageProcessor ip2 = imp2.getProcessor();
		ip2.setColor(Color.BLACK);
		ip2.fillOutside(roi0);
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		// riempirò l'interno di nero
		ip2.invert();
		minSizePixels = 1000;
		maxSizePixels = 30000;

		Roi roi1 = analisi0(imp2, minSizePixels, maxSizePixels);

		if (roi1 == null) {
			UtilAyv.showImageMaximized(imp1);
			UtilAyv.showImageMaximized(imp2);
			MyLog.waitHere("roi0==null");
			return null;
		}
		ImagePlus imp112 = imp2.duplicate();
		ImageProcessor ip112 = imp112.getProcessor();
		ip112.setColor(Color.WHITE);
		ip112.fill(roi1);
		IJ.run(imp112, "Invert", "");

		return imp112;
	}

	public static Roi analisi0(ImagePlus imp1, int minSizePixel,
			int maxSizePixel) {
		int options = ParticleAnalyzer.SHOW_OUTLINES
				+ ParticleAnalyzer.ADD_TO_MANAGER;
		int measurements = 0;
		ResultsTable rt0 = new ResultsTable();
		RoiManager rm0 = new RoiManager(false);
		ParticleAnalyzer pa1 = new ParticleAnalyzer(options, measurements, rt0,
				minSizePixel, maxSizePixel);
		ParticleAnalyzer.setRoiManager(rm0);
		pa1.setHideOutputImage(true);
		pa1.analyze(imp1);
		if (rm0.getCount() == 1) {
			Roi[] vetRoi = rm0.getRoisAsArray();
			Roi roi0 = vetRoi[0];
			return roi0;
		} else
			return null;
	}

	/***
	 * La strategia 1 è dedicata al circolo interno di rods nelle macchine
	 * Siemens
	 * 
	 * @param imp1
	 * @return
	 */

	public static ImagePlus strategia1(ImagePlus imp1) {

		boolean noBlack = true;
		boolean noWhite = false;
		boolean doWhite = true;
		boolean doSet = false;
		boolean doLog = false;

		// IJ.run(imp1, "Invert", "");
		ImagePlus imp12 = MyAutoThreshold.threshold(imp1, "Li", noBlack,
				noWhite, doWhite, doSet, doLog);
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		// riempirò l'esterno di nero
		int minSizePixels = 1000;
		int maxSizePixels = 30000;
		Roi roi1 = analisi0(imp12, minSizePixels, maxSizePixels);
		if (roi1 == null) {
			MyLog.waitHere("roi1==null");
			return null;
		}
		ImagePlus imp112 = imp12.duplicate();
		ImageProcessor ip112 = imp112.getProcessor();
		ip112.setColor(Color.BLACK);
		ip112.fillOutside(roi1);
		// IJ.run(imp112, "Invert", "");

		imp112.setTitle("strategia2: Li+Invert");
		if (imp112 == null)
			MyLog.waitHere("imp112 == null");
		return imp112;
	}

	public static ImagePlus combina(ImagePlus imp1, ImagePlus imp2) {
		if (imp1 == null)
			return null;
		if (imp2 == null)
			return null;
		ImageCalculator ic1 = new ImageCalculator();
		ImagePlus imp3 = ic1.run("OR create", imp1, imp2);
		IJ.run(imp3, "Invert", "");
		return imp3;
	}

	public static ImagePlus strategia2(ImagePlus imp1) {

		boolean noBlack = true;
		boolean noWhite = false;
		boolean doWhite = true;
		boolean doSet = false;
		boolean doLog = false;

		ImagePlus imp2 = MyAutoThreshold.threshold(imp1, "Mean", noBlack,
				noWhite, doWhite, doSet, doLog);
		IJ.run(imp2, "Invert", "");
		imp2.setTitle("strategia3: Mean+Invert");
		return imp2;
	}

	public static ImagePlus strategia3(ImagePlus imp1) {

		boolean noBlack = false;
		boolean noWhite = false;
		boolean doWhite = true;
		boolean doSet = false;
		boolean doLog = false;

		ImagePlus imp2 = MyAutoThreshold.threshold(imp1, "Huang", noBlack,
				noWhite, doWhite, doSet, doLog);
		IJ.run(imp2, "Invert", "");
		imp2.setTitle("strategia1: Hunag+Invert");
		return imp2;
	}

	public static ImagePlus strategia4(ImagePlus imp1) {

		boolean noBlack = false;
		boolean noWhite = false;
		boolean doWhite = false;
		boolean doSet = false;
		boolean doLog = false;

		ImagePlus imp11 = imp1.duplicate();
		IJ.run(imp11, "Invert", "");
		ImagePlus imp2 = MyAutoThreshold.threshold(imp11, "Li", noBlack,
				noWhite, doWhite, doSet, doLog);
		IJ.run(imp2, "Invert", "");
		imp2.setTitle("strategia1: Invert+Li+Invert");
		// ora analizzo l'immagine cercando il profilo tondo del fantoccio,
		// riempirò l'esterno di nero
		int minSizePixels = 10000;
		int maxSizePixels = 300000;
		Roi roi0 = analisi0(imp2, minSizePixels, maxSizePixels);
		if (roi0 == null)
			return null;
		ImagePlus imp12 = imp2.duplicate();
		ImageProcessor ip12 = imp12.getProcessor();
		ip12.setColor(Color.BLACK);
		ip12.fillOutside(roi0);
		// ora analizzo l'immagine cercando il profilo quadro dell'inserto,
		// riempirò l'interno di nero
		ip12.invert();
		minSizePixels = 1000;
		maxSizePixels = 30000;
		Roi roi1 = analisi0(imp12, minSizePixels, maxSizePixels);
		if (roi1 == null)
			MyLog.waitHere("roi1==null");
		ImagePlus imp112 = imp12.duplicate();
		ImageProcessor ip112 = imp112.getProcessor();
		ip112.setColor(Color.WHITE);
		ip112.fill(roi1);
		IJ.run(imp112, "Invert", "");
		return imp112;
	}

	public static ResultsTable analisi(ImagePlus imp1, boolean verbose,
			int timeout) {
		int options = ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES
				+ ParticleAnalyzer.SHOW_OUTLINES;
		int measurements = Measurements.CENTER_OF_MASS + Measurements.AREA;
		double minSize = 0.;
		double maxSize = 100;
		double minCirc = 0;
		double maxCirc = 1;
		ResultsTable rt0 = new ResultsTable();

		ParticleAnalyzer pa1 = new ParticleAnalyzer(options, measurements, rt0,
				minSize, maxSize, minCirc, maxCirc);
		pa1.setHideOutputImage(true);
		boolean ok = pa1.analyze(imp1);
		if (verbose) {
			ImagePlus imp100 = pa1.getOutputImage();
			rt0.show("Results");
			UtilAyv.showImageMaximized(imp100);
			MyLog.waitHere("Oggetti trovati", debug, timeout);
			imp100.close();
		}
		return rt0;

	}

	public void overlayRodNumbers(ImagePlus imp1, int diamRoi, boolean verbose) {

		Roi roi2 = imp1.getRoi();
		Rectangle rec2 = roi2.getBounds();
		imp1.setRoi(new OvalRoi(0, 0, 0, 0));
		double bx = rec2.getX();
		double by = rec2.getY();
		double bw = rec2.getWidth() / imp1.getWidth();
		CustomCanvasGeneric ccg1 = new CustomCanvasGeneric(imp1);
		double[] cx1 = { 245, 105, 245, 380, 175, 310, 75, 160, 335, 425, 175,
				310, 105, 245, 380, 245 };
		double[] cy1 = { 40, 95, 130, 95, 160, 160, 230, 230, 230, 230, 305,
				305, 370, 330, 370, 430 };
		double[] cx2 = { 250, 270, 250, 230 };
		double[] cy2 = { 240, 260, 280, 260 };
		ccg1.setOffset(bx, by, bw);
		ccg1.setPosition1(cx1, cy1);
		ccg1.setColor1(Color.red);
		ccg1.setPosition2(cx2, cy2);
		ccg1.setColor2(Color.green);
		if (verbose) {
			ImageWindow iw1 = new ImageWindow(imp1, ccg1);
			iw1.maximize();
		}
	}

	void selfTestAyv() {
		if (new InputOutput().checkJar(MyConst.TEST_FILE)) {
			int userSelection2 = UtilAyv.siemensGe();
			switch (userSelection2) {
			case 1: {
				// GE
				String home1 = findTestImages();
				String path1 = home1 + "/HWSA2_testP7";
				int[] vetX = MyConst.P7_X_POINTS_TESTGE;
				int[] vetY = MyConst.P7_Y_POINTS_TESTGE;
				boolean ok = testExcecution(path1, vetX, vetY, -10, -17, true);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				return;
			}
			case 2:
				// Siemens
				String home1 = findTestImages();
				String path1 = home1 + "/HWSA_testP7";
				int[] vetX = MyConst.P7_X_POINTS_TESTSIEMENS;
				int[] vetY = MyConst.P7_Y_POINTS_TESTSIEMENS;
				boolean ok = testExcecution(path1, vetX, vetY, 0, 20, true);
				if (ok)
					MyMsg.msgTestPassed();
				else
					MyMsg.msgTestFault();
				UtilAyv.afterWork();
				return;
			}
		}
		UtilAyv.noTest2();
	}

	void selfTestSilent() {
		String[] list = { "HWSA2_testP7" };
		String[] path2 = new InputOutput().findListTestImages2(
				MyConst.TEST_FILE, list, MyConst.TEST_DIRECTORY);
		String path1 = path2[0];

		int[] vetX = MyConst.P7_X_POINTS_TESTSIEMENS;
		int[] vetY = MyConst.P7_Y_POINTS_TESTSIEMENS;
		boolean ok = testExcecution(path1, vetX, vetY, 0, 0, false);
		if (ok) {
			IJ.log("Il test di p7rmn_ WARP è stato SUPERATO");
		} else {
			IJ.log("Il test di p7rmn_ WARP evidenzia degli ERRORI");
		}
		return;
	}

	boolean testExcecution(String path1, int[] vetX, int[] vetY, int offX,
			int offY, boolean verbose) {
		ImagePlus imp1 = null;
		if (verbose) {
			imp1 = UtilAyv.openImageMaximized(path1);
		} else {
			imp1 = UtilAyv.openImageNoDisplay(path1, false);
		}
		double dimPixel2 = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));
		double diamRoi1 = (double) MyConst.P7_DIAM_ROI / dimPixel2;
		int diamRoi = (int) diamRoi1;
		boolean circular = true;
		UtilAyv.presetRoi(imp1, diamRoi, offX, offY, circular);
		if (verbose)
			IJ.wait(500);
		overlayRodNumbers(imp1, diamRoi, verbose);
		Polygon poli1 = UtilAyv.clickSimulation(imp1, vetX, vetY);
		boolean ok = UtilAyv.verifyResults2(poli1.xpoints, poli1.ypoints, vetX,
				vetY, "della ROD ");
		return ok;
	}

	/**
	 * genera una directory temporanea e vi estrae le immagini di test da
	 * test2.jar
	 * 
	 * @return home1 path della directory temporanea con le immagini di test
	 */
	private String findTestImages() {
		InputOutput io = new InputOutput();
		io.extractFromJAR(MyConst.TEST_FILE, "HWSA_testP7", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "HWSA2_testP7", "./Test2/");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY)
				.getPath();
		return (home1);
	}

	private static void msgPositionRoi() {
		ButtonMessages.ModelessMsg("Posizionare la ROI  e premere CONTINUA",
				"CONTINUA");
	}

	private static void msgRedo(int nPunti) {
		IJ.showMessage("--- A T T E N Z I O N E ---",
				"Sono stati selezionati solo " + nPunti
						+ " anzichè 36  punti,\n--- R I F A R E ---");
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
	static int[][] referenceSiemens() {

		int[][] vetReference = { { 3, 0 }, { 257, 86 }, { 252, 105 },
				{ 384, 138 }, { 123, 139 }, { 127, 156 }, { 380, 157 },
				{ 254, 177 }, { 253, 196 }, { 189, 203 }, { 317, 203 },
				{ 189, 222 }, { 318, 222 }, { 90, 267 }, { 171, 267 },
				{ 334, 267 }, { 417, 267 }, { 88, 286 }, { 170, 287 },
				{ 335, 286 }, { 417, 286 }, { 315, 331 }, { 187, 332 },
				{ 189, 351 }, { 317, 351 }, { 251, 358 }, { 252, 377 },
				{ 124, 397 }, { 381, 397 }, { 380, 414 }, { 124, 416 },
				{ 251, 448 }, { 252, 467 }, { 256, 247 }, { 223, 279 },
				{ 288, 282 }, { 254, 313 }, { 0, 0 } };

		return vetReference;
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
		lista[0] = "L'immagine in input viene processata con AutoThreshold.threshold+Median";
		lista[1] = "L'immagine binaria viene invertita";
		lista[2] = "Viene utilizzato Analyze Particles (size=50-1000 circularity=0.1-1.00)\n "
				+ "per identificare i gel e misurarne misurare i Bounding Rectangles";
		lista[3] = "Misurando i BoundingRectangles otteniamo questi risultati";
		lista[4] = "Posizione ROI in verde";
		lista[5] = "L'immagine in input viene processata con AutoThreshold.threshold+Huang";
		lista[6] = "Numero nuovi oggetti localizzati= ";
		lista[7] = "Numero ROI rilevate errato, posizioni ROI in verde";

		// ---------+-----------------------------------------------------------+
		String out = lista[select];
		return out;
	}

}
