"""
 helper functions
"""

import subprocess
import threading

def run_command(cmd, discard=False, children=None):
    """
    Runs command, capturing the output if the discard flag is True
    Returns a success flag and the output.
    If the command returns a non-zero exit code, the success flag is
    set to False and the error message is returned.
    The output is returned as a string (usually UTF-8 encoded
    if not otherwise configured)
    """
    output = ""
    try:
        if not discard:
            raw_output = subprocess.check_output(cmd, shell=True, bufsize=4096,
                                                 stderr=subprocess.STDOUT)
            output = raw_output.decode("UTF-8")
        else:
            # run the command in the background
            child = subprocess.Popen(cmd, shell=True)
            # remember child to be able to clean up processes later
            if children is not None: 
                children.append(child)

        success = True
    except subprocess.CalledProcessError as cpe:
        output = "Command '%s' failed with code %s: %s" % (
            cmd, cpe.returncode, cpe.output)
        success = False

    return success, output

class Counter(object):
    
    def __init__(self):
        self.lock = threading.Lock()
        self.num = 0

    def get(self):
        return self.num

    def increment(self):
        with self.lock:
            self.num+=1
            return self.num

    def decrement(self):
        with self.lock:
            self.num-=1
            return self.num
    