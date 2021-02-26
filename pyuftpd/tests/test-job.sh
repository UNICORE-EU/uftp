nc -N localhost 54435 <<EOF
request-type=uftp-transfer-request
user=schuller
secret=123
file=/tmp/___UFTP___MULTI___FILE___SESSION___MODE___
END

EOF
