
from pyqtgraph.Qt import QtGui, QtCore
import numpy as np
import pyqtgraph as pg

import pyqtgraph.examples
#pyqtgraph.examples.run()

#QtGui.QApplication.setGraphicsSystem('raster')
app = QtGui.QApplication([])
#mw = QtGui.QMainWindow()
#mw.resize(800,800)
np.set_printoptions(suppress=True, precision=2, linewidth=200)


win = pg.GraphicsWindow(title="Basic plotting examples")
win.resize(1000,600)
win.setWindowTitle('pyqtgraph example: Plotting')

# Enable antialiasing for prettier plots
pg.setConfigOptions(antialias=True)

#p1 = win.addPlot(title="Basic array plotting", y=np.genfromtxt('yeet.dat'))

p3 = win.addPlot(title="Drawing with points")

names = ["INSPEED", "ENDANGLE", "TOTALTICKS", "DRIFTTICKS", "OUTSPEED", "OUTOFFSETX", "OUTOFFSETY"]
rows = [1, 4]

driftyBoi = np.genfromtxt('turn.drift.txt', skip_header=1)
allRows = np.arange(0, 7)
skippedRows, cunt = np.unique(np.append(allRows, rows), return_counts=True)

skippedRows = skippedRows[cunt == 1]
skippedRows = sorted(skippedRows)[::-1]

for row in skippedRows:
    driftyBoi = np.delete(driftyBoi, row, 1)

p3.plot(driftyBoi, pen=None, symbolBrush=(255,0,0), symbolPen='w')
#p3.plot(np.genfromtxt('yeet.dat'), pen=None, symbolBrush=(255,0,0), symbolPen='w')
#p3.plot(np.genfromtxt('ml.dat'), pen=None, symbolBrush=(0,255,0), symbolPen='w')
p3.setLabel('left', names[rows[1]])
p3.setLabel('bottom', names[rows[0]])

## Start Qt event loop unless running in interactive mode or using pyside.
if __name__ == '__main__':
    import sys
    if (sys.flags.interactive != 1) or not hasattr(QtCore, 'PYQT_VERSION'):
        QtGui.QApplication.instance().exec_()
