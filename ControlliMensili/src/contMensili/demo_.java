package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.measure.Measurements;
import ij.plugin.PlugIn;
import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.ReadDicom;
import utils.UtilAyv;

/**
 * Questo plugin serve a testare eventuali problemi, prima di segnalarli a Wayne
 * Rasband od altri.
 *
 * @author alberto
 *
 */
public class demo_ implements PlugIn, Measurements {

	@Override
	public void run(String args) {

		String home1 = findTestImages();
		String path1 = "C:\\programmi2\\imagej\\H43_HUSSA";
//		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		imp1.show();
		// ImageCanvas canvas = imp1.getWindow().getCanvas();

		double dimPix = ReadDicom.readDouble(
				ReadDicom.readSubstring(ReadDicom.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING), 1));

		// xstart=256 ystart= 159 xend= 0yend= 90
		double xStartRefline = 256.0;
		double yStartRefline = 159.0;
		double xEndRefline = 0;
		double yEndRefline = 90.0;
		IJ.log("dimPix= " + dimPix);
		IJ.log("width= " + imp1.getWidth());

		int xStartReflineScreen = (int) (xStartRefline / dimPix);
		int yStartReflineScreen = (int) (yStartRefline / dimPix);
		int xEndReflineScreen = (int) (xEndRefline / dimPix);
		int yEndReflineScreen = (int) (yEndRefline / dimPix);

		xStartReflineScreen= limiter(imp1.getWidth(),xStartReflineScreen );
		xEndReflineScreen= limiter(imp1.getWidth(),xEndReflineScreen );
		yStartReflineScreen= limiter(imp1.getHeight(),yStartReflineScreen );
		yEndReflineScreen= limiter(imp1.getHeight(),yEndReflineScreen );



		imp1.setRoi(new Line(xStartReflineScreen, yStartReflineScreen, xEndReflineScreen, yEndReflineScreen));
		imp1.updateAndDraw();
		Roi roi1 = imp1.getRoi();

		IJ.log("IJversion= "+IJ.getFullVersion());
		IJ.log("xstart= " + xStartReflineScreen + " ystart= " + yStartReflineScreen + " xend= " + xEndReflineScreen
				+ " yend= " + yEndReflineScreen);
//		imp1.killRoi();
		double[] profi1 = ((Line) roi1).getPixels(); // profilo non mediato
		for (int i1 = 0; i1 < profi1.length; i1++) {
			IJ.log("pixel= " + i1 + " val= " + profi1[i1]);
		}

//		IJ.log("pixel 0 of the line= " + profi1[0]);
		new WaitForUserDialog("Do something, then click OK.").show();

	}

	public static int limiter(int limit, int val) {

		if (val<1) {
			val=1;
		}
		if (val>limit-1) {
			val=limit-1;
		}
		return val;
	}

	/**
	 * genera una directory temporanea e vi estrae le immagini di test da test2.jar
	 *
	 * @return home1 path della directory temporanea con le immagini di test
	 */
	private String findTestImages() {

		InputOutput io = new InputOutput();

		io.extractFromJAR(MyConst.TEST_FILE, "HR2A_testP4", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "HR2A2_testP4", "./Test2/");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY).getPath();
		return (home1);
	} // findTestImages

	public void showImages() {

		ImagePlus imp1 = UtilAyv.openImageMaximized("./Test2/B003_TestP2");
		ImageWindow iw1 = WindowManager.getCurrentWindow();
		ImagePlus imp2 = UtilAyv.openImageMaximized("./Test2/C001_TestP10");
		ImageWindow iw2 = WindowManager.getCurrentWindow();

		MyLog.waitHere("win1 to front");
		if (true) {
			WindowManager.setCurrentWindow(iw1);
		}
		MyLog.waitHere("win2 to front");
		if (true) {
			WindowManager.setCurrentWindow(iw2);
		}
		MyLog.waitHere("win1 to front");
		if (true) {
			WindowManager.setCurrentWindow(iw1);
		}
		MyLog.waitHere("win2 to front");
		if (true) {
			WindowManager.setCurrentWindow(iw2);
		}
	}

}
