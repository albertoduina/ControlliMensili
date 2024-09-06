package contMensili;

import java.awt.Color;
import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Line;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.util.Tools;
import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.ProfileUtils;
import utils.ReadDicom;
import utils.TableSequence;
import utils.UtilAyv;

/***************************************************************
 * 
 * CONTIENE LE PARTI COMUNI INDISPENSABILI ALLE ALTRE VERSIONI
 * 
 **************************************************************/
public class p6rmn_COMMON {

	public static void saveLog_COMMON(String path, String name, boolean thisSPY) {
		if (thisSPY) {
			IJ.selectWindow("Log");
			IJ.saveAs("text", path + "\\" + name);
		}
	}

	public static String[] loadPath_COMMON(String autoArgs) {
		String fileDir = Prefs.get("prefer.string1", "./test2/");
		String[][] iw2ayvTable = new TableSequence().loadTable(fileDir + MyConst.SEQUENZE_FILE);
		int[] vetRiga = UtilAyv.decodeTokens(autoArgs);
		String[] path = new String[vetRiga.length];
		for (int i1 = 0; i1 < vetRiga.length; i1++) {
			path[i1] = TableSequence.getPath(iw2ayvTable, vetRiga[i1]);
		}
		return path;
	}

	public static double[] readReferences_COMMON(ImagePlus imp1) {
		int rows = imp1.getHeight();
		float dimPixel = ReadDicom
				.readFloat(ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));

		double[] vetReference = new double[4];
		vetReference[0] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnAx", "" + 50.0 / dimPixel));
		vetReference[1] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnAy", "" + 60.0 / dimPixel));
		vetReference[2] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnBx", "" + 50.0 / dimPixel));
		vetReference[3] = ReadDicom.readDouble(Prefs.get("prefer.p6rmnBy", "" + 215.0 / dimPixel));
		if (interdiction_COMMON(vetReference, rows)) {
			vetReference[0] = 50.0 / dimPixel;
			vetReference[1] = 60.0 / dimPixel;
			vetReference[2] = 50.0 / dimPixel;
			vetReference[3] = 215.0 / dimPixel;
		}
		return vetReference;

	}

	/**
	 * impedisce che nelle preferenze di ImageJ vengano memorizzati segmenti con
	 * valori assurdi, crea un area al di fuori della quale il valore memorizzato e'
	 * quello di default, inoltre non permette che il segmento sia piu' corto di 10
	 * pixel
	 * 
	 * @param vetLine coordinate linea [ xStart, yStart, xEnd, yEnd ]
	 * @param matrix  matrice immagine
	 * @return true se interdetta memorizzazione
	 */
	public static boolean interdiction_COMMON(double[] vetLine, int matrix) {

		if (vetLine[0] < 10)
			return true;
		if (vetLine[0] > (matrix - 10))
			return true;
		if (vetLine[2] < 10)
			return true;
		if (vetLine[2] > (matrix - 10))
			return true;
		double len = Math.sqrt(Math.pow(vetLine[0] - vetLine[2], 2) + Math.pow(vetLine[1] - vetLine[3], 2));
		if (len < 10)
			return true;
		// se viene ribaltato il segmento interviene
		if (vetLine[1] > vetLine[3])
			return true;

		return false;
	}

	public static void saveReferences_COMMON(ImagePlus imp1) {
		Line line1 = (Line) imp1.getRoi();
		Prefs.set("prefer.p6rmnAx", "" + line1.x1);
		Prefs.set("prefer.p6rmnAy", "" + line1.y1);
		Prefs.set("prefer.p6rmnBx", "" + line1.x2);
		Prefs.set("prefer.p6rmnBy", "" + line1.y2);
	}

	public static void saveDebugImage_COMMON(ImagePlus imp1, String spydir, String name, int contaxx, boolean thisSPY) {
		ImagePlus impS3 = imp1.flatten();
		// IJ.log(MyLog.qui() + " spydir= " + spydir + " name= " + name);

		String newName = spydir + "\\" + name + "@" + IJ.pad(contaxx, 3) + ".jpg";
		FileSaver fs = new FileSaver(impS3);
		// FileSaver.setJpegQuality(100);
		if (thisSPY)
			fs.saveAsJpeg(newName);
	}

	/**
	 * Inverte il segnale del profilo
	 * 
	 * @param profile1
	 * @return
	 */
	public static double[][] profiInverter_COMMON(double[][] profile1) {

		double[] profileX = ProfileUtils.decodeX(profile1);
		double[] profileY = ProfileUtils.decodeY(profile1);

		// double max = ArrayUtils.vetMax(profileY);
		// double min = ArrayUtils.vetMin(profileY);

		for (int i1 = 0; i1 < profileY.length; i1++) {
			profileY[i1] = profileY[i1] * (-1);
		}

		return ProfileUtils.encode(profileX, profileY);
	}

	/**
	 * Trasforma il profilo Y aggiungendo i valori X
	 * 
	 * @param profi1
	 * @return
	 */
	public static double[][] profiTransform_COMMON(double[] profi1) {

		double[][] out1 = new double[profi1.length][2];
		for (int i1 = 0; i1 < profi1.length; i1++) {
			out1[i1][0] = (double) i1;
			out1[i1][1] = (double) profi1[i1];
		}
		return out1;
	}

	public static double[] getLinePixels_COMMON(ImagePlus imp1, double x1, double y1, double x2, double y2) {

		imp1.setRoi(new Line(x1, y1, x2, y2));
		imp1.updateAndDraw();
		Roi roi1 = imp1.getRoi();
		double[] profiM1 = ((Line) roi1).getPixels();
		return profiM1;
	}

//	/**
//	 * assegna un profilo su cui effettuare i calcoli
//	 * 
//	 * @param imp1 immagine su cui effettuare il profilo
//	 * @param x1   coordinata x start profilo
//	 * @param y1   coordinata y start profilo
//	 * @param x2   coordinata x end profilo
//	 * @param y2   coordinata y end profilo
//	 * @return profilo wideline
//	 */
//	public static double[][] getLinePixels_FITTER(ImagePlus imp1, int x1, int y1, int x2, int y2) {
//
//		imp1.setRoi(new Line(x1, y1, x2, y2));
//		imp1.updateAndDraw();
//		Roi roi1 = imp1.getRoi();
//		double[] profiM1 = ((Line) roi1).getPixels();
//		double[][] out1 = ProfileUtils.encode(profiM1);
//		return out1;
//	}

	/**
	 * display del profilo fit con linea a meta' altezza
	 * 
	 * @param bslab    true=slab false=cuneo
	 * @param bLabelSx true=label a sx nel plot, false=label a dx
	 * @param sTitolo  titolo del grafico
	 * @param bFw      true=scritte x FWHM
	 */
	public static Plot createPlot1_COMMON(double[][] profile1, double[] fwhm, boolean bslab, boolean bFw,
			boolean bLabelSx, String sTitolo, double spessoreTeorico, double dimPixel) {

		double labPos;

		double[] profileY = ProfileUtils.decodeY(profile1);
		double[] a = Tools.getMinMax(profileY);

		double min1 = a[0];
		double max1 = a[1];
		if (Math.abs(max1) > Math.abs(min1)) {
			max1 = a[1];
			min1 = a[0];
		} else {
			max1 = a[0];
			min1 = a[1];
		}

		double half = (max1 - min1) / 2 + min1;

		int len1 = profile1.length;
		double[] xcoord1 = new double[len1];
		for (int j = 0; j < len1; j++)
			xcoord1[j] = j;

		Plot plot = new Plot(sTitolo, "pixel", "valore", Plot.AUTO_POSITION);
		plot.setColor(Color.black);
		plot.addLabel(0.01, 0.99, sTitolo);
		plot.setColor(Color.red);
		plot.add("line", ProfileUtils.decodeX(profile1), ProfileUtils.decodeY(profile1));

		if (bslab) {
//			plot.setLimits(0, len1, min1, max1);
//			plot.setLimits(0, len1, min1, Double.NaN);
		} else {
//			plot.setLimits(0, len1, min1, 30);
//			plot.setLimits(0, len1, min1, Double.NaN);
		}
		plot.setLineWidth(1);

		// linea a mezza altezza
		plot.setColor(Color.green);
		double[] pointX = new double[2];
		double[] pointY = new double[2];
		pointX[0] = 0;
		pointX[1] = len1;
		pointY[0] = half;
		pointY[1] = half;
		plot.addPoints(pointX, pointY, Plot.LINE);

		plot.setColor(Color.black);
		if (bLabelSx)
			labPos = 0.10;
		else
			labPos = 0.60;
		if (bFw) {
			plot.addLabel(labPos, 0.75, "peak / 2 =   " + IJ.d2s(half, 2));
			plot.addLabel(labPos, 0.80, "fwhm  [pixels]  =  " + IJ.d2s(fwhm[0], 2));
			plot.addLabel(labPos, 0.85, "thick teorica   =  " + IJ.d2s(spessoreTeorico, 2));
			double aux7 = fwhm[0] * dimPixel * Math.sin(Math.toRadians(11.3));
			plot.addLabel(labPos, 0.90, "thick calcolata RAW =  " + IJ.d2s(aux7, 2));
		}

		return plot;

	}

//	/**
//	 * un semplicissimo ma funzionale smoothing
//	 * 
//	 * @param profile1 array su cui eseguire lo smoothing
//	 */
//	public void smooth(double[] profile1) {
//
//		for (int i1 = 1; i1 < profile1.length - 1; i1++)
//			profile1[i1] = (profile1[i1 - 1] + profile1[i1] + profile1[i1 + 1]) / 3.0;
//
//		return;
//	}

	/**
	 * SMOOTH
	 * 
	 * @param profile1
	 */

	/**
	 * un semplicissimo ma funzionale smoothing
	 * 
	 * @param profile1 array su cui eseguire lo smoothing
	 */
	public static void smooth2_COMMON(double profile1[]) {

		IJ.log("prima " + profile1[0] + " " + profile1[1] + " " + profile1[2] + " " + profile1[3] + " " + profile1[4]);
		int len = profile1.length;
		for (int i1 = 2; i1 < len - 2; i1++)
			profile1[i1] = (profile1[i1 - 2] + profile1[i1 - 1] + profile1[i1] + profile1[i1 + 1] + profile1[i1 + 2])
					/ 5.0;
		profile1[0] = profile1[3];
		profile1[1] = profile1[4];
		profile1[len - 1] = profile1[len - 5];
		profile1[len - 2] = profile1[len - 4];
		IJ.log("dopo " + profile1[0] + " " + profile1[1] + " " + profile1[2] + " " + profile1[3] + " " + profile1[4]);
		return;
	}

	public static boolean myDoubleCompare_COMMON(double uno, double due, double tolleranza) {

		double diff = Math.abs(uno - due);
		if (diff <= tolleranza)
			return true;
		else
			return false;
	}

	public static double[] spessStrato_AAPM100_COMMON(double a2, double b2, double spessTeor, double dimPix) {

		double FWHM = spessoreAAPM100_COMMON(a2, b2, dimPix);

		// attenzione, devo far VERIFICARE le formule di ACCURATEZZA ed ERRORE
		// SPERIMENTALE, queste le ho improvvisate MALE !!!
		double percAccurSpess = 100.0 * ((FWHM - spessTeor) / spessTeor);
		double mma2 = a2 * dimPix;
		double mmb2 = b2 * dimPix;
		double mmErroreSper = 100.0 * ((FWHM - spessTeor) / ((mma2 + mmb2) / 2));

		double[] out = new double[4];
		out[0] = FWHM;
		out[1] = FWHM;
		out[2] = mmErroreSper;
		out[3] = percAccurSpess;

		return out;
	}

	/**
	 * La formula della correzione FWHM applicata e'ricavata dalla pubblicazione
	 * AAPM 100. In un primo momento pareva dare risultati fasulli, poi ho capito
	 * che l'angolo a chi fanno riferimento non e'quello del materiale del wedge ma
	 * quello posto tra le due superfici inclinate, wedge o slab che siano
	 * 
	 * ATTENZIONE, QUESTO E'NEL FILE COMMON PERCHE' USATO SIA DA OLD CHE IMPROVED
	 * CHE FITTER
	 * 
	 * @param a2
	 * @param b2
	 * @param dimPix
	 * @return
	 */
	public static double spessoreAAPM100_COMMON(double a2, double b2, double dimPix) {

		// calcolo dell'angolo tra le sue slab / wedges

		double angolo = 180 - 11.3 * 2; // QUESTO E'L'ANGOLO CONTRO CUI PICCHIARE IL NASO
		double a1 = a2 * dimPix;
		double b1 = b2 * dimPix;

		double cos = Math.cos(Math.toRadians(angolo));
		double sin = Math.sin(Math.toRadians(angolo));
		double sum = a1 + b1;
		double product = a1 * b1;

		// la formula e'quella data nella pubblicazione. Per semplificare ho calcolato
		// esternamente somme e prodotti. Ho preferito elevare al quadrato
		// moltiplicando e non usare le libreria matematica.

		double mmFWHM = (sum * cos + Math.sqrt((sum * sum) * (cos * cos) + (4 * product * (sin * sin)))) / (2 * sin);

		return mmFWHM;
	}

	/**
	 * si occupa di gestire cancellazione e creazione della cartella in cui salvare
	 * log e immagini di SPY
	 */
	public static String spyDirTree_COMMON(String path[], boolean step) {

		// IJ.log("---------------------------------------- --------------");
		File f1 = new File(path[0]);
		String aux2 = f1.getParent();
		int pos1 = aux2.lastIndexOf(File.separatorChar);
		String spyfirst = aux2.substring(0, pos1 + 1) + "STICAZZI";
		String spysecond = aux2.substring(pos1);

		File theDir1 = new File(spyfirst);

		boolean via = Prefs.get("prefer.p6rmnSTART", false);
		Prefs.set("prefer.p6rmnSTART", false);

		if (via) {
			boolean ok1 = InputOutput.deleteDir(theDir1);
//			if (ok1) {
//				IJ.log(MyLog.qui() + " method= " + MyLog.method() + ">> cancellata cartella " + spyfirst);
//			} else {
//				IJ.log(MyLog.qui() + " method= " + MyLog.method() + ">> errore cancellazione " + spyfirst);
//			}
		}
		boolean ok2 = InputOutput.createDirMultiple(spyfirst + spysecond);
//		if (ok2) {
//			IJ.log(MyLog.qui() + " method= " + MyLog.method() + ">> create cartelle " + spyfirst + spysecond);
//		} else {
//			IJ.log(MyLog.qui() + " method= " + MyLog.method() + ">> errore creazione " + spyfirst + spysecond);
//		}

		String spyout = spyfirst + "\\" + spysecond;
		return spyout;
	}

	/**
	 * analisi di un profilo normale con ricerca punti sopra e sotto meta' altezza
	 * verificato risultato identico ad ORIGINALE
	 * 
	 * @param profile1 profilo da analizzare
	 * @param bSlab    true=slab false=cuneo
	 * @return isd[0] punto profilo sotto half a sx
	 * @return isd[1] punto profilo sopra half a sx
	 * @return isd[2] punto profilo sotto half a dx
	 * @return isd[3] punto profilo sopra half a dx
	 */
	public static int[] analPlot1_ORIGINAL_COMMON(double profile1[], boolean bSlab) {

		int sopra1 = 0;
		int sotto1 = 0;
		int sopra2 = 0;
		int sotto2 = 0;
		double max1 = 0;
		int i1;
		int[] isd;
		isd = new int[4];
		isd[0] = 0;
		isd[1] = 0;
		isd[2] = 0;
		isd[3] = 0;
		int len1 = profile1.length;

		double[] a = Tools.getMinMax(profile1);
		double min1 = a[0];
		if (bSlab)
			max1 = a[1];
		else
			max1 = 0;

		// calcolo meta' altezza
		double half = (max1 - min1) / 2 + min1;
		// ricerca valore < half partendo da SX
		for (i1 = 0; i1 < len1 - 1; i1++) {
			if (profile1[i1] < half) {
				sotto1 = i1;
				break;
			}
		}
		// torno indietro e cerco il primo valore > half
		for (i1 = sotto1; i1 > 0; i1--) {
			if (profile1[i1] > half) {
				sopra1 = i1;
				break;
			}
		}
		// ricerca valore < half partendo da DX
		for (i1 = len1 - 1; i1 > 0; i1--) {
			if (profile1[i1] < half) {
				sotto2 = i1;
				break;
			}
		}
		// torno indietro e cerco il primo valore > half
		for (i1 = sotto2; i1 < len1 - 1; i1++) {
			if (profile1[i1] > half) {
				sopra2 = i1;
				break;
			}
		}
		isd[0] = sotto1;
		isd[1] = sopra1;
		isd[2] = sotto2;
		isd[3] = sopra2;
		return isd;
	} // analPlot1

	/**
	 * analisi di un profilo normale con ricerca punti sopra e sotto meta' altezza
	 * questa versione modificata effettua la ricerca partendo dal picco invece che
	 * dalle estremita'.
	 * 
	 * A RIPOSO DA'LO STESSO RISULTATO DI ORIGINAL VENGONO RESTITUITE LE COORDINATE
	 * X DEI PUNTI
	 * 
	 * @param profile1 profilo da analizzare
	 * @param bSlab    true=slab false=cuneo
	 * @return isd[0] punto profilo sotto half a sx
	 * @return isd[1] punto profilo sopra half a sx
	 * @return isd[2] punto profilo sotto half a dx
	 * @return isd[3] punto profilo sopra half a dx
	 */
	public static int[] analPlot1_MODIF_COMMON(double profile1[], boolean bSlab) {

		int sopra1 = 9999;
		int sotto1 = 9999;
		int sopra2 = 9999;
		int sotto2 = 9999;

		double maxY = 9999;
		int[] isd;
		isd = new int[4];
		isd[0] = 0;
		isd[1] = 0;
		isd[2] = 0;
		isd[3] = 0;
		int len1 = profile1.length;

		double[] a = Tools.getMinMax(profile1);
		double minY = a[0];
		if (bSlab)
			maxY = a[1];
		else
			maxY = 0;

		// calcolo meta' altezza
		double half = (maxY - minY) / 2 + minY;
		// ricerca coordinata x del punto minimo
		double minX = 9999;
		for (int i1 = 0; i1 < len1; i1++) {
			if (profile1[i1] == minY) {
				minX = (double) i1;
				break;
			}
		}

		// ========== SINISTRA =======
		// ricerca valore < half partendo dal punto minimo
		for (int i1 = (int) minX; i1 > 0; i1--) {
			if (profile1[i1] > half) {
				sotto1 = i1;
				sopra1 = sotto1 + 1;
				break;
			}
		}

		// ========== DESTRA =======
		// ricerca valore < half partendo dal punto minimo
		for (int i1 = (int) minX; i1 < len1; i1++) {
			if (profile1[i1] > half) {
				sotto2 = i1;
				sopra2 = sotto2 - 1;
				break;
			}
		}

		isd[0] = sotto1;
		isd[1] = sopra1;
		isd[2] = sotto2;
		isd[3] = sopra2;

		return isd;
	} // analPlot1

	public static double pix2mm_COMMON(double in1, double dimPixel) {

		double angolo = 11.3;
		double out1 = in1 * dimPixel * Math.sin(Math.toRadians(angolo));

		return out1;
	}

}
