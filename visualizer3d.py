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

def loadData(str):
    data = np.genfromtxt(str, skip_header=1)

    driftyBoi = np.reshape(data[:, rows[0]], (data.shape[0], 1))
    driftyBoi = np.insert(driftyBoi, 1, data[:, rows[1]], axis=1)
    driftyBoi = np.insert(driftyBoi, 2, data[:, rows[2]], axis=1)

    #normalize data
    driftyBoi[:, 0] /= np.max(np.abs(driftyBoi[:, 0]))
    driftyBoi[:, 1] /= np.max(np.abs(driftyBoi[:, 1]))
    driftyBoi[:, 2] /= np.max(np.abs(driftyBoi[:, 2]))


    driftColor = np.reshape(driftyBoi[:, 2], (driftyBoi.shape[0], 1))

    driftColor = np.insert(driftColor, [1], [0.5, 1, 1], axis=1)

    return (driftyBoi, driftColor)

oldData, oldColor = loadData('turn.drift.txt')
newData, newColor = loadData('turn.drift2.txt')
newColor[:, 2] = 0
newColor[:, 1] = 1 - newColor[:, 0]

driftyBoi = np.append(oldData, newData, axis=0)
driftColor = np.append(oldColor, newColor, axis=0)

#(0.5, 0.5, 1, 1)
p1 = gl.GLScatterPlotItem(pos=driftyBoi, color=driftColor, size=5)
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