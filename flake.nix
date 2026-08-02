{
  description = "I Am Zombie? NeoForge development shell";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
      runtimeLibraries = with pkgs; [
        glibc
        stdenv.cc.cc.lib

        libglvnd
        openal
        flite
        vulkan-loader

        alsa-lib
        libpulseaudio
        pipewire
        udev

        libdrm
        wayland
        libxkbcommon

        libx11
        libxext
        libxcursor
        libxrandr
        libxi
        libxxf86vm
        libxfixes
        libxrender
        libxcb
        libxau
        libxdmcp
        libxinerama
      ];
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk25
          pkgs.pulseaudio
        ];

        JAVA_HOME = pkgs.jdk25.home;
        LD_LIBRARY_PATH = pkgs.lib.concatStringsSep ":" [
          (pkgs.lib.makeLibraryPath runtimeLibraries)
          "/run/opengl-driver/lib"
        ];
      };
    };
}
