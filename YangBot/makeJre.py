import subprocess
import os
import shutil
import zipfile

basePath = os.path.dirname(os.path.abspath(__file__))
os.chdir(basePath)
print('Current Path:', os.getcwd())

print("Executing: gradlew.bat --quiet installDist")
result = subprocess.run(['gradlew.bat', '--quiet', 'installDist'], stdout=subprocess.PIPE)
output = '\n'.join(result.stdout.decode('utf-8').split(os.linesep))
print(output)

basePath = os.path.join(basePath, 'build\\install')
os.chdir(basePath)
print('Current Path:', os.getcwd())

jarPath = os.path.join(basePath, 'YangBot\\lib')
allJars = [f for f in os.listdir(jarPath) if os.path.isfile(os.path.join(jarPath, f)) and f.endswith('.jar')]
allJars = ['YangBot\\lib\\'+f for f in allJars]

print('Including Jars:')
print('\n'.join(allJars))
print()

print('Searching modules...')
result = subprocess.run(['jdeps', '--print-module-deps', '-cp', "'"+(';'.join(allJars))+"'"] + allJars, stdout=subprocess.PIPE)
modules = result.stdout.decode('utf-8').split(os.linesep)[0]
print()
print('Modules needed:', modules)
print('Generating runtime...')
if os.path.isdir('java-runtime'):
    shutil.rmtree('java-runtime')
    print('Deleted old runtime')

result = subprocess.run(['jlink', '--no-header-files', '--no-man-pages', '--add-modules', modules, '--output', 'java-runtime'], stdout=subprocess.PIPE)
output = '\n'.join(result.stdout.decode('utf-8').split(os.linesep))
print(output)

# Alter start script
basePath = os.path.join(basePath, 'YangBot\\bin')
os.chdir(basePath)
print('Current Path:', os.getcwd())
print('Altering start script...')

os.remove('YangBot') # remove linux script
with open("YangBot.bat", "r+") as f:
     old = f.read() # read everything in the file
     old = old.split('\n')
     for i in range(len(old)):
        if old[i].startswith('set APP_HOME'):
            old.insert(i+1, 'echo %JAVA_HOME%')
            old.insert(i+1, 'set JAVA_HOME=%APP_HOME%\\..\\java-runtime')
            break

     f.seek(0) # rewind
     f.write('\n'.join(old)) # write 

print('Zipping up...')
basePath = os.path.join(basePath, '..\\..\\..\\')
os.chdir(basePath)
print('Current Path:', os.getcwd())
shutil.make_archive('YangBot', 'zip', 'install')

print('Script done')