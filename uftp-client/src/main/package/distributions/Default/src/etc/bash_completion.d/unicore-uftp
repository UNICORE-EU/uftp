
_uftp()
{
  local cur prev commands global_opts opts
  COMPREPLY=()
  cur=`_get_cword`
  prev="${COMP_WORDS[COMP_CWORD-1]}"
  commands="authenticate checksum cp get-share info ls mkdir put-share rm share sync tunnel"
  global_opts="--auth --group --help --identity --client --oidc-agent --password --user --verbose --help"


  # parsing for uftp command word (2nd word in commandline.
  # uftp <command> [OPTIONS] <args>)
  if [ $COMP_CWORD -eq 1 ]; then
    COMPREPLY=( $(compgen -W "${commands}" -- ${cur}) )
    return 0
  fi

  # looking for arguments matching to command
  case "${COMP_WORDS[1]}" in
    authenticate)
    opts="$global_opts --streams --bandwithlimit --encrypt --compress"
    ;;
    checksum)
    opts="$global_opts --recurse --algorithm --bytes"
    ;;
    cp)
    opts="$global_opts --split-threshold --threads --encrypt --compress --preserve --recurse --bytes --streams --archive --bandwithlimit --resume"
    ;;
    get-share)
    opts="$global_opts --streams --bandwithlimit --encrypt --compress"
    ;;
    info)
    opts="$global_opts --raw"
    ;;
    ls)
    opts="$global_opts --human-readable"
    ;;
    mkdir)
    opts="$global_opts "
    ;;
    put-share)
    opts="$global_opts --streams --bandwithlimit --encrypt --compress"
    ;;
    rm)
    opts="$global_opts --recurse --quiet"
    ;;
    share)
    opts="$global_opts --delete --access --server --write --list"
    ;;
    sync)
    opts="$global_opts --streams --bandwithlimit --encrypt --compress"
    ;;
    tunnel)
    opts="$global_opts --listen"
    ;;

  esac

  COMPREPLY=( $(compgen -W "${opts}" -- ${cur}) )
  
  _filedir

}

complete -o filenames -F _uftp uftp
