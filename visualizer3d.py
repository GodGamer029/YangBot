from pyqtgraph.Qt import QtCore, QtGui
import pyqtgraph as pg
import pyqtgraph.opengl as gl
import numpy as np

np.set_printoptions(suppress=True, precision=2, linewidth=200)


## Create a GL View widget to display data
app = QtGui.QApplication([])
w = gl.GLViewWidget()
w.show()
w.setWindowTitle('pyqtgraph example: GLSurfacePlot')
w.setCameraPosition(distance=50)

## Add a grid to the view
g = gl.GLGridItem()
g.scale(2,2,1)
g.setDepthValue(10)  # draw grid after surfaces since they may be translucent
w.addItem(g)

names = ["INSPEED", "ENDANGLE", "TOTALTICKS", "DRIFTTICKS", "OUTSPEED", "OUTOFFSETX", "OUTOFFSETY"]
rows = [0, 4, 1]

data = np.genfromtxt('turn.drift.txt', skip_header=1)
'''
allRows = np.arange(0, 7)
skippedRows, cunt = np.unique(np.append(allRows, rows), return_counts=True)

skippedRows = skippedRows[cunt == 1]
skippedRows = sorted(skippedRows)[::-1]


for row in skippedRows:
    driftyBoi = np.delete(driftyBoi, row, 1)
'''

driftyBoi = np.reshape(data[:, rows[0]], (data.shape[0], 1))
driftyBoi = np.insert(driftyBoi, 1, data[:, rows[1]], axis=1)
driftyBoi = np.insert(driftyBoi, 2, data[:, rows[2]], axis=1)


print(driftyBoi)


driftyBoi[:, 0] /= np.max(np.abs(driftyBoi[:, 0]))
driftyBoi[:, 1] /= np.max(np.abs(driftyBoi[:, 1]))
driftyBoi[:, 2] /= np.max(np.abs(driftyBoi[:, 2]))


driftColor = np.reshape(driftyBoi[:, 2], (driftyBoi.shape[0], 1))
#driftColorG = np.reshape(driftyBoi[:, 1], (driftyBoi.shape[0], 1))

driftColor = np.insert(driftColor, [1], [0.5, 1, 1], axis=1)
print(driftyBoi)
#(0.5, 0.5, 1, 1)
p1 = gl.GLScatterPlotItem(pos=driftyBoi, color=driftColor, size=2)
p1.scale(10, 10, 10)
w.addItem(p1)

axis = gl.GLAxisItem()
axis.scale(20, 10, 5)
w.addItem(axis)

## Start Qt event loop unless running in interactive mode.
if __name__ == '__main__':
    import sys
    if (sys.flags.interactive != 1) or not hasattr(QtCore, 'PYQT_VERSION'):
        QtGui.QApplication.instance().exec_()