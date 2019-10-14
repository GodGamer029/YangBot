
from pyqtgraph.Qt import QtGui, QtCore
import numpy as np
import pyqtgraph as pg

import pyqtgraph.examples
#pyqtgraph.examples.run()

#QtGui.QApplication.setGraphicsSystem('raster')
app = QtGui.QApplication([])
#mw = QtGui.QMainWindow()
#mw.resize(800,800)

win = pg.GraphicsWindow(title="Basic plotting examples")
win.resize(1000,600)
win.setWindowTitle('pyqtgraph example: Plotting')

# Enable antialiasing for prettier plots
pg.setConfigOptions(antialias=True)

#p1 = win.addPlot(title="Basic array plotting", y=np.genfromtxt('yeet.dat'))

p3 = win.addPlot(title="Drawing with points")
p3.plot(np.genfromtxt('yeet.dat'), pen=None, symbolBrush=(255,0,0), symbolPen='w')
p3.plot(np.genfromtxt('ml.dat'), pen=None, symbolBrush=(0,255,0), symbolPen='w')
p3.setLabel('left', "Time", units='sec')
p3.setLabel('bottom', "Euler angle", units='s')

## Start Qt event loop unless running in interactive mode or using pyside.
if __name__ == '__main__':
    import sys
    if (sys.flags.interactive != 1) or not hasattr(QtCore, 'PYQT_VERSION'):
        QtGui.QApplication.instance().exec_()
