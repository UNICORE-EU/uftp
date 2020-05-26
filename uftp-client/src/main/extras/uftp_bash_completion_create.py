#!/usr/bin/env python

from __future__ import with_statement
from subprocess import Popen, PIPE, STDOUT

TEMPLATE = "uftp_bash_completion.template"
OUTPUT = "uftp_bash_completion.sh"
CMD = "uftp"

######################################################################

def find_commands():
    commands = []
    print "Running UFTO to get the list of commands ... "
    p = Popen([CMD], stdout=PIPE, stderr=STDOUT)
    p.wait()
    for line in p.stdout.readlines():
        if not line.startswith(" "):
            continue
        else:
            commands.append(line.split()[0])

    return commands


def find_options(command):
    options = []
    print "Getting options for %s" % command
    p = Popen([CMD, command, "-h"], stdout=PIPE, stderr=STDOUT)
    p.wait()
    for line in p.stdout.readlines():
        if not line.startswith(" -"):
            continue
        else:
            s = line.split()[0]
            options.append(s.split(",")[1])

    return options


######################################################################

with open(TEMPLATE) as f:
    output = f.read()
    
commands = sorted(find_commands())
global_opts = find_options("info")
global_opts.append("--help")
global_opts.remove("--raw")
case_body = ""


for command in commands:

    opts = find_options(command)
    opts = set(opts) - set(global_opts)
    s = '    %s)\n    opts="$global_opts %s"\n    ;;\n' % (command,
                                                           " ".join(opts))
    case_body += s


output = output % {"commands": " ".join(commands),
                   "global_opts": " ".join(global_opts),
                   "case_body": case_body}


with open(OUTPUT, "w") as f:
    f.write(output)
