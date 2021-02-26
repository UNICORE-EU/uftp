nc -N localhost 54435 <<EOF
request-type=uftp-transfer-request
user=tester1
secret=test123
file=___UFTP___MULTI___FILE___SESSION___MODE___
END

EOF
