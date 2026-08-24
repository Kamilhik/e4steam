function initializeCoreMod() {
    var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var FieldInsnNode = Java.type('org.objectweb.asm.tree.FieldInsnNode');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
    var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');

    var loginStartName = ASMAPI.mapMethod('func_147316_a');
    var tryAcceptName = ASMAPI.mapMethod('func_147326_c');
    var onlineModeName = ASMAPI.mapMethod('func_71266_T');
    var networkManagerField = ASMAPI.mapField('field_147333_a');
    var loginProfileField = ASMAPI.mapField('field_147337_i');
    var connectName = ASMAPI.mapMethod('func_146367_a');
    var playTickName = ASMAPI.mapMethod('func_73660_a');
    var playConnectionField = ASMAPI.mapField('field_147371_a');
    var targetClass = 'net/minecraft/network/login/ServerLoginNetHandler';
    var connectTargetClass = 'net/minecraft/client/gui/screen/ConnectingScreen';
    var playTargetClass = 'net/minecraft/network/play/ServerPlayNetHandler';
    var hookClass = 'link/e4steam/retro/forge/E4steamForge114Hooks';

    return {
        'e4steam_forge_114_login': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.network.login.ServerLoginNetHandler'
            },
            'transformer': function(classNode) {
                var patchedAuthentication = false;
                var patchedIdentity = false;

                for (var methodIndex = 0; methodIndex < classNode.methods.size(); methodIndex++) {
                    var method = classNode.methods.get(methodIndex);
                    if (method.name === loginStartName || method.name === 'processLoginStart'
                            || method.name === 'func_147316_a') {
                        for (var instruction = method.instructions.getFirst(); instruction !== null;
                                instruction = instruction.getNext()) {
                            if (instruction.getOpcode() === Opcodes.INVOKEVIRTUAL
                                    && instruction.owner === 'net/minecraft/server/MinecraftServer'
                                    && (instruction.name === onlineModeName
                                        || instruction.name === 'isServerInOnlineMode'
                                        || instruction.name === 'func_71266_T')
                                    && instruction.desc === '()Z') {
                                var connectionArgument = new InsnList();
                                connectionArgument.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                connectionArgument.add(new FieldInsnNode(
                                        Opcodes.GETFIELD,
                                        targetClass,
                                        networkManagerField,
                                        'Lnet/minecraft/network/NetworkManager;'));
                                method.instructions.insertBefore(instruction, connectionArgument);
                                method.instructions.set(instruction, new MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        hookClass,
                                        'useMojangAuthentication',
                                        '(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/network/NetworkManager;)Z',
                                        false));
                                patchedAuthentication = true;
                                break;
                            }
                        }
                    }

                    if (method.name === tryAcceptName || method.name === 'tryAcceptPlayer'
                            || method.name === 'func_147326_c') {
                        var identityPrefix = new InsnList();
                        identityPrefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        identityPrefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        identityPrefix.add(new FieldInsnNode(
                                Opcodes.GETFIELD,
                                targetClass,
                                loginProfileField,
                                'Lcom/mojang/authlib/GameProfile;'));
                        identityPrefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        identityPrefix.add(new FieldInsnNode(
                                Opcodes.GETFIELD,
                                targetClass,
                                networkManagerField,
                                'Lnet/minecraft/network/NetworkManager;'));
                        identityPrefix.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                hookClass,
                                'bindSteamIdentity',
                                '(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/network/NetworkManager;)Lcom/mojang/authlib/GameProfile;',
                                false));
                        identityPrefix.add(new FieldInsnNode(
                                Opcodes.PUTFIELD,
                                targetClass,
                                loginProfileField,
                                'Lcom/mojang/authlib/GameProfile;'));
                        method.instructions.insert(identityPrefix);
                        patchedIdentity = true;
                    }
                }

                if (!patchedAuthentication || !patchedIdentity) {
                    throw new Error('e4steam could not patch the Forge 1.14 login state machine');
                }
                return classNode;
            }
        },
        'e4steam_forge_114_direct_address': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.client.gui.screen.ConnectingScreen'
            },
            'transformer': function(classNode) {
                var patchedAddress = false;
                for (var methodIndex = 0; methodIndex < classNode.methods.size(); methodIndex++) {
                    var method = classNode.methods.get(methodIndex);
                    if ((method.name === connectName || method.name === 'connect'
                            || method.name === 'func_146367_a')
                            && method.desc === '(Ljava/lang/String;I)V') {
                        var vanillaAddress = new LabelNode();
                        var addressPrefix = new InsnList();
                        addressPrefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        addressPrefix.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                hookClass,
                                'acceptDirectSteamAddress',
                                '(Ljava/lang/String;)Z',
                                false));
                        addressPrefix.add(new JumpInsnNode(Opcodes.IFEQ, vanillaAddress));
                        addressPrefix.add(new InsnNode(Opcodes.RETURN));
                        addressPrefix.add(vanillaAddress);
                        method.instructions.insert(addressPrefix);
                        patchedAddress = true;
                        break;
                    }
                }
                if (!patchedAddress) {
                    throw new Error('e4steam could not patch the Forge 1.14 direct address connector');
                }
                return classNode;
            }
        },
        'e4steam_forge_114_keep_alive': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.network.play.ServerPlayNetHandler'
            },
            'transformer': function(classNode) {
                var patchedKeepAlive = false;
                for (var methodIndex = 0; methodIndex < classNode.methods.size(); methodIndex++) {
                    var method = classNode.methods.get(methodIndex);
                    if ((method.name === playTickName || method.name === 'tick'
                            || method.name === 'func_73660_a') && method.desc === '()V') {
                        for (var instruction = method.instructions.getFirst(); instruction !== null;
                                instruction = instruction.getNext()) {
                            if (instruction.getOpcode() === Opcodes.LDC
                                    && String(instruction.cst) === '15000') {
                                var connectionArgument = new InsnList();
                                connectionArgument.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                connectionArgument.add(new FieldInsnNode(
                                        Opcodes.GETFIELD,
                                        playTargetClass,
                                        playConnectionField,
                                        'Lnet/minecraft/network/NetworkManager;'));
                                method.instructions.insertBefore(instruction, connectionArgument);
                                method.instructions.set(instruction, new MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        hookClass,
                                        'keepAliveIntervalMillis',
                                        '(Lnet/minecraft/network/NetworkManager;)J',
                                        false));
                                patchedKeepAlive = true;
                                break;
                            }
                        }
                    }
                    if (patchedKeepAlive) {
                        break;
                    }
                }
                if (!patchedKeepAlive) {
                    throw new Error('e4steam could not patch the Forge 1.14 Steam keep-alive window');
                }
                return classNode;
            }
        }
    };
}
