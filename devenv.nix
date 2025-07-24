{ pkgs, lib, config, inputs, ... }:

let
  pkgs-unstable = import inputs.nixpkgs-unstable { 
    system = pkgs.stdenv.system;
  };

  # sbt = pkgs-unstable.sbt.override { jre = pkgs-unstable.jdk24_headless; };
in

{
  name = "blindspot";
  env.BLINDSPOT_ENV = "Development";

  packages = [ 
    pkgs.git 
    pkgs-unstable.k9s
    pkgs-unstable.kubectl
    pkgs-unstable.kubectx
    pkgs-unstable.nodejs_24
  ];

  languages.java = {
    enable = true;
    jdk.package = pkgs-unstable.jdk24_headless;
  };

  languages.scala = {
    enable = true;
    sbt.enable = true;
  };

  languages.javascript = {
    enable = true;
    package = pkgs-unstable.nodejs_24;
    yarn.enable = true;
    yarn.install.enable = true;
  };

  enterShell = ''
    echo "~~~ blindspot ~~~"
    echo JAVA_HOME=$JAVA_HOME
    export PATH=$PATH

    export SBT_OPTS="--sun-misc-unsafe-memory-access=allow \
                     --enable-native-access=ALL-UNNAMED"
  '';

  enterTest = ''
    sbt test
  '';
}
