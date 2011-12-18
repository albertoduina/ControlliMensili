package contMensili;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.measure.Measurements;
import ij.plugin.PlugIn;

import java.awt.Color;

import utils.InputOutput;
import utils.MyConst;
import utils.MyLog;
import utils.ReadDicom;
import utils.UtilAyv;

public class demo_ implements PlugIn, Measurements {

	public void run(String args) {

		String home1 = findTestImages();
		String path1 = home1 + "/HR2A2_testP4";
		ImagePlus imp1 = UtilAyv.openImageMaximized(path1);
		// ImageCanvas canvas = imp1.getWindow().getCanvas();

		double dimPix = ReadDicom
				.readDouble(ReadDicom.readSubstring(ReadDicom
						.readDicomParameter(imp1, MyConst.DICOM_PIXEL_SPACING),
						1));
		
		
	

		// double mag = canvas.getMagnification();
		// double wid = imp1.getWidth();
		double xStartRefline = 45.0;
		double yStartRefline = 113.0;
		double xEndRefline = 150.0;
		double yEndRefline = 187.0;
		IJ.log("dimPix= " + dimPix);
		// int xStartReflineScreen = canvas.screenXD(xStartRefline);
		// int yStartReflineScreen = canvas.screenYD(yStartRefline);
		// int xEndReflineScreen = canvas.screenXD(xEndRefline);
		// int yEndReflineScreen = canvas.screenYD(yEndRefline);

		int xStartReflineScreen = (int) (xStartRefline / dimPix);
		int yStartReflineScreen = (int) (yStartRefline / dimPix);
		int xEndReflineScreen = (int) (xEndRefline / dimPix);
		int yEndReflineScreen = (int) (yEndRefline / dimPix);
		imp1.setRoi(new Line(xStartReflineScreen, yStartReflineScreen,
				xEndReflineScreen, yEndReflineScreen));
		imp1.updateAndDraw();
		new WaitForUserDialog("Do something, then click OK.").show();

		int[] vetX = new int[2];
		int[] vetY = new int[2];
		vetX[0] = (int) xStartRefline;
		vetX[1] = (int) xEndRefline;
		vetY[0] = (int) yStartRefline;
		vetY[1] = (int) yEndRefline;

		PointRoi pRoi = new PointRoi(vetX, vetY, vetX.length);
		imp1.setOverlay(pRoi, Roi.getColor(), 2, Color.white);
		imp1.updateAndDraw();
		new WaitForUserDialog("Do something, then click OK.").show();

	}

	/**
	 * genera una directory temporanea e vi estrae le immagini di test da
	 * test2.jar
	 * 
	 * @return home1 path della directory temporanea con le immagini di test
	 */
	private String findTestImages() {

		InputOutput io = new InputOutput();

		io.extractFromJAR(MyConst.TEST_FILE, "HR2A_testP4", "./Test2/");
		io.extractFromJAR(MyConst.TEST_FILE, "HR2A2_testP4", "./Test2/");
		String home1 = this.getClass().getResource(MyConst.TEST_DIRECTORY)
				.getPath();
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
