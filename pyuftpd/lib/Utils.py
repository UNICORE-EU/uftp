"""
 helper functions
"""

import re
import os
import os.path
import sys
import subprocess

def expand_variables(message):
    """
    Expands $HOME and $USER into the values from the current environment
    """
    message = message.replace("$HOME", os.environ['HOME'])
    message = message.replace("$LOGNAME", os.environ['LOGNAME'])
    return message.replace("$USER", os.environ['USER'])


def addperms(path, mode):
    """
    Adds the mode permissions to those which are already set for the file.
    """
    curmode = os.stat(path)[0]
    mode = curmode | mode
    os.chmod(path, mode)


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


def run_and_report(cmd, connector):
    """
    Runs the command and report success/failure with output
    """
    (success, output) = run_command(cmd)
    if not success:
        connector.failed(output)
    else:
        connector.ok(output)
