
_uftp()
{
  local cur prev commands global_opts opts
  COMPREPLY=()
  cur=`_get_cword`
  prev="${COMP_WORDS[COMP_CWORD-1]}"
  commands="cp get info ls mkdir put rm share sync tunnel"
  global_opts="--auth --group --identity --oidc-agent --password --user --verbose --help"


  # parsing for uftp command word (2nd word in commandline.
  # uftp <command> [OPTIONS] <args>)
  if [ $COMP_CWORD -eq 1 ]; then
    COMPREPLY=( $(compgen -W "${commands}" -- ${cur}) )
    return 0
  fi

  # looking for arguments matching to command
  case "${COMP_WORDS[1]}" in
    cp)
    opts="$global_opts --bytes --threads --encrypt --client --resume --archive --recurse --compress --bandwithlimit --preserve --streams --split-threshold"
    ;;
    get)
    opts="$global_opts --compress --streams --bandwithlimit --encrypt --client"
    ;;
    info)
    opts="$global_opts --raw"
    ;;
    ls)
    opts="$global_opts --streams --client --human-readable --bandwithlimit --compress --encrypt"
    ;;
    mkdir)
    opts="$global_opts --compress --streams --bandwithlimit --encrypt --client"
    ;;
    put)
    opts="$global_opts --compress --streams --bandwithlimit --encrypt --client"
    ;;
    rm)
    opts="$global_opts --quiet --encrypt --client --bandwithlimit --compress --streams"
    ;;
    share)
    opts="$global_opts --list --anonymous --delete --write"
    ;;
    sync)
    opts="$global_opts --compress --streams --bandwithlimit --encrypt --client"
    ;;
    tunnel)
    opts="$global_opts --streams --client --bandwithlimit --compress --encrypt --listen"
    ;;

  esac

  COMPREPLY=( $(compgen -W "${opts}" -- ${cur}) )
  
  _filedir

}

complete -o filenames -F _uftp uftp
